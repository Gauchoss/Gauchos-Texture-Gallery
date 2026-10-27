package de.gaucho.gallery;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Baut aus der Auswahl ein ganz normales Resource Pack (Ordner in .minecraft/resourcepacks)
 * und aktiviert es. So funktionieren die Texturen mit allem, was Resource Packs kann –
 * ohne riskante Eingriffe in den Spielcode.
 */
public final class PackBuilder {
	public static final String FOLDER = "GauchosTextureGallery";
	public static final String PACK_ID = "file/" + FOLDER;
	public static final String NAMESPACE = "gauchos_gallery";

	// Minecraft 1.21.11 = Resource-Pack-Format 75
	private static final String PACK_MCMETA = """
			{
			  "pack": {
			    "description": "§6Gauchos Texture Gallery§r\\nWird automatisch von der Mod verwaltet",
			    "min_format": 75,
			    "max_format": 99
			  }
			}
			""";

	private PackBuilder() {}

	public static Path packDir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(FOLDER);
	}

	public static String previewPath(int index) {
		return "textures/preview/p" + index + ".png";
	}

	public static String backgroundPath(int index) {
		return "textures/background/b" + index + ".png";
	}

	public static String soundEventPath(int index) {
		return "kill.s" + index;
	}

	/**
	 * Schreibt den Pack-Ordner neu (nur wenn sich etwas geaendert hat).
	 * Laeuft im Hintergrund-Thread.
	 *
	 * @return true, wenn sich der Inhalt geaendert hat und neu geladen werden muss
	 */
	public static boolean build(GalleryRepository.Snapshot snap, Map<String, String> selections, GalleryRepository repo) throws IOException {
		// Zielpfad im Pack -> SHA der Datei im Cache
		Map<String, String> plan = new LinkedHashMap<>();

		// 1) Ausgewaehlte Texturen
		for (Categories.Category cat : Categories.all()) {
			GalleryRepository.Option opt = snap.find(cat.id(), selections.get(cat.id()));
			if (opt == null) continue;
			for (Map.Entry<String, List<String>> e : cat.files().entrySet()) {
				String sha = opt.files.get(e.getKey());
				String meta = opt.files.get(e.getKey() + ".mcmeta");
				for (String target : e.getValue()) {
					String p = "assets/minecraft/textures/" + target;
					// Hinweis: Minecraft nimmt die .mcmeta (Animation) aus demselben Pack wie die PNG.
					// Ohne eigene .mcmeta ist die eigene Textur also einfach nicht animiert.
					if (sha != null) plan.put(p, sha);
					if (meta != null) plan.put(p + ".mcmeta", meta);
				}
			}
		}

		// 2) Vorschaubilder fuer das Menue
		for (List<GalleryRepository.Option> opts : snap.options().values()) {
			for (GalleryRepository.Option o : opts) {
				if (o.previewIndex >= 0 && o.previewSha != null) {
					plan.put("assets/" + NAMESPACE + "/" + previewPath(o.previewIndex), o.previewSha);
				}
			}
		}

		// 3) Hintergrundbilder (weich skaliert, damit grosse Bilder nicht pixelig aussehen)
		Map<String, String> texts = new LinkedHashMap<>();
		for (GalleryRepository.Background b : snap.backgrounds()) {
			String p = "assets/" + NAMESPACE + "/" + backgroundPath(b.index);
			plan.put(p, b.sha);
			texts.put(p + ".mcmeta", "{\"texture\":{\"blur\":true}}");
		}

		// 4) Kill-Sounds
		JsonObject soundsJson = new JsonObject();
		for (GalleryRepository.KillSound s : snap.killSounds()) {
			plan.put("assets/" + NAMESPACE + "/sounds/kill/s" + s.index + ".ogg", s.sha);
			JsonObject sound = new JsonObject();
			sound.addProperty("name", NAMESPACE + ":kill/s" + s.index);
			JsonArray arr = new JsonArray();
			arr.add(sound);
			JsonObject event = new JsonObject();
			event.add("sounds", arr);
			soundsJson.add(soundEventPath(s.index), event);
		}
		String soundsText = soundsJson.toString();

		// Fingerabdruck: hat sich seit dem letzten Mal etwas geaendert?
		String fingerprint = sha1(plan + "|" + texts + "|" + soundsText + "|" + PACK_MCMETA);
		Path dir = packDir();
		Path stateFile = dir.resolve(".gauchos_state");
		if (Files.exists(stateFile) && Files.readString(stateFile, StandardCharsets.UTF_8).equals(fingerprint)) {
			return false;
		}

		// Neu schreiben
		deleteRecursively(dir);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("pack.mcmeta"), PACK_MCMETA, StandardCharsets.UTF_8);
		for (Map.Entry<String, String> e : plan.entrySet()) {
			Path target = dir.resolve(e.getKey());
			Files.createDirectories(target.getParent());
			Files.copy(repo.cacheFile(e.getValue()), target, StandardCopyOption.REPLACE_EXISTING);
		}
		for (Map.Entry<String, String> e : texts.entrySet()) {
			Path target = dir.resolve(e.getKey());
			Files.createDirectories(target.getParent());
			Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
		}
		if (!snap.killSounds().isEmpty()) {
			Path sj = dir.resolve("assets/" + NAMESPACE + "/sounds.json");
			Files.createDirectories(sj.getParent());
			Files.writeString(sj, soundsText, StandardCharsets.UTF_8);
		}
		Files.writeString(stateFile, fingerprint, StandardCharsets.UTF_8);
		return true;
	}

	/** Aktiviert den Pack (oben in der Liste) und laedt Ressourcen neu. Muss im Render-Thread laufen. */
	public static void apply(Minecraft mc, boolean contentChanged) {
		PackRepository packs = mc.getResourcePackRepository();
		packs.reload();
		if (!packs.getAvailableIds().contains(PACK_ID)) {
			GauchosGallery.LOGGER.error("Resource Pack {} wurde nicht gefunden", PACK_ID);
			return;
		}
		List<String> selected = new ArrayList<>(packs.getSelectedIds());
		boolean alreadyActive = selected.contains(PACK_ID);
		if (alreadyActive && !contentChanged) return;

		if (!alreadyActive) {
			selected.add(PACK_ID); // ganz oben = hoechste Prioritaet
			packs.setSelected(selected);
		}
		List<String> before = new ArrayList<>(mc.options.resourcePacks);
		mc.options.updateResourcePacks(packs); // speichert options.txt, laedt neu falls Liste geaendert
		if (before.equals(mc.options.resourcePacks)) {
			mc.reloadResourcePacks();
		}
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		Path root = dir.toAbsolutePath().normalize();
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				if (p.toAbsolutePath().normalize().startsWith(root)) Files.deleteIfExists(p);
			}
		}
	}

	private static String sha1(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			return Integer.toHexString(s.hashCode());
		}
	}
}
