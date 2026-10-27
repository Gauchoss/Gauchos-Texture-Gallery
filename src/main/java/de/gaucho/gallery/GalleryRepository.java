package de.gaucho.gallery;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Laedt die Galerie aus dem GitHub-Repo (Ordner "gallery/").
 * Nur wer Schreibrechte auf das Repo hat (= du), kann dort Dateien hochladen.
 * Die Mod liest nur – deine Freunde koennen also nichts veraendern.
 */
public class GalleryRepository {

	/** Eine auswaehlbare Variante innerhalb einer Kategorie. */
	public static final class Option {
		public final String name;
		/** Datei-Schluessel (siehe Categories) -> Git-SHA der Datei (= Dateiname im Cache). */
		public final Map<String, String> files = new LinkedHashMap<>();
		/** Index der Vorschau-Textur im generierten Pack, -1 = keine Vorschau. */
		public int previewIndex = -1;
		public String previewSha;

		Option(String name) {
			this.name = name;
		}
	}

	public static final class KillSound {
		public final String name;
		public final String sha;
		public final int index;

		KillSound(String name, String sha, int index) {
			this.name = name;
			this.sha = sha;
			this.index = index;
		}
	}

	/** Hintergrundbild fuers Menue (gallery/backgrounds/*.png). */
	public static final class Background {
		public final String name;
		public final String sha;
		public final int index;
		public final int width;
		public final int height;

		Background(String name, String sha, int index, int width, int height) {
			this.name = name;
			this.sha = sha;
			this.index = index;
			this.width = width;
			this.height = height;
		}
	}

	public record Snapshot(Map<String, List<Option>> options, List<KillSound> killSounds, List<Background> backgrounds) {
		public static final Snapshot EMPTY = new Snapshot(Map.of(), List.of(), List.of());

		public Background findBackground(String name) {
			if (name == null) return null;
			for (Background b : backgrounds) {
				if (b.name.equals(name)) return b;
			}
			return null;
		}

		public List<Option> optionsFor(String categoryId) {
			return options.getOrDefault(categoryId, List.of());
		}

		public Option find(String categoryId, String optionName) {
			if (optionName == null) return null;
			for (Option o : optionsFor(categoryId)) {
				if (o.name.equals(optionName)) return o;
			}
			return null;
		}

		public KillSound findSound(String name) {
			if (name == null) return null;
			for (KillSound s : killSounds) {
				if (s.name.equals(name)) return s;
			}
			return null;
		}
	}

	private static final String KILL_SOUND_FOLDER = "killsounds";
	private static final String BACKGROUND_FOLDER = "backgrounds";

	private final Path cacheDir = FabricLoader.getInstance().getConfigDir().resolve("gauchos_gallery_cache");
	private final HttpClient http = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final AtomicBoolean loading = new AtomicBoolean(false);
	private final String repo;
	private final String branch;

	private volatile Snapshot snapshot = Snapshot.EMPTY;
	private volatile String status = "Galerie wird noch nicht geladen";

	public GalleryRepository() {
		String r = "";
		String b = "main";
		try (InputStream in = GalleryRepository.class.getResourceAsStream("/gauchos_gallery_source.json")) {
			if (in != null) {
				JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				r = o.get("repo").getAsString().trim();
				b = o.get("branch").getAsString().trim();
			}
		} catch (Exception e) {
			GauchosGallery.LOGGER.error("gauchos_gallery_source.json fehlt/kaputt", e);
		}
		this.repo = r;
		this.branch = b.isEmpty() ? "main" : b;
	}

	public Snapshot snapshot() {
		return snapshot;
	}

	public String status() {
		return status;
	}

	public boolean isLoading() {
		return loading.get();
	}

	public Path cacheFile(String sha) {
		return cacheDir.resolve(sha);
	}

	/** Startet das Laden im Hintergrund. Gibt null zurueck, wenn schon geladen wird. */
	public CompletableFuture<Snapshot> refresh() {
		if (!loading.compareAndSet(false, true)) return null;
		return CompletableFuture.supplyAsync(() -> {
			try {
				Snapshot s = load();
				snapshot = s;
				return s;
			} catch (Exception e) {
				GauchosGallery.LOGGER.error("Galerie konnte nicht geladen werden", e);
				status = "Fehler: " + e.getMessage();
				return snapshot;
			} finally {
				loading.set(false);
			}
		});
	}

	private Snapshot load() throws Exception {
		if (repo.isEmpty() || repo.contains("DEIN-GITHUB-NAME")) {
			status = "Kein GitHub-Repo eingestellt (siehe README)";
			return Snapshot.EMPTY;
		}
		Files.createDirectories(cacheDir);
		status = "Lade Galerie von GitHub …";

		// 1) Dateiliste des Repos holen (ein einziger API-Aufruf)
		String treeJson;
		Path treeCache = cacheDir.resolve("tree.json");
		boolean offline = false;
		try {
			String url = "https://api.github.com/repos/" + repo + "/git/trees/" + enc(branch) + "?recursive=1";
			HttpResponse<String> res = http.send(request(url).header("Accept", "application/vnd.github+json").build(),
					HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) {
				throw new IOException("GitHub antwortet mit " + res.statusCode()
						+ (res.statusCode() == 404 ? " (Repo privat oder falscher Name?)" : "")
						+ (res.statusCode() == 403 ? " (zu viele Anfragen – später nochmal)" : ""));
			}
			treeJson = res.body();
			Files.writeString(treeCache, treeJson, StandardCharsets.UTF_8);
		} catch (Exception e) {
			if (!Files.exists(treeCache)) throw e;
			GauchosGallery.LOGGER.warn("GitHub nicht erreichbar, nutze letzte gespeicherte Galerie: {}", e.getMessage());
			treeJson = Files.readString(treeCache, StandardCharsets.UTF_8);
			offline = true;
		}

		// 2) Dateiliste auswerten
		Map<String, TreeMap<String, Option>> byCategory = new LinkedHashMap<>();
		List<String[]> sounds = new ArrayList<>(); // name, sha
		List<String[]> backgrounds = new ArrayList<>(); // name, sha
		Map<String, String> shaToPath = new LinkedHashMap<>();

		JsonArray tree = JsonParser.parseString(treeJson).getAsJsonObject().getAsJsonArray("tree");
		for (JsonElement el : tree) {
			JsonObject entry = el.getAsJsonObject();
			if (!"blob".equals(entry.get("type").getAsString())) continue;
			String path = entry.get("path").getAsString();
			String sha = entry.get("sha").getAsString();
			if (!path.startsWith("gallery/")) continue;
			String[] parts = path.substring("gallery/".length()).split("/");
			String folder = parts[0].toLowerCase();

			if (folder.equals(KILL_SOUND_FOLDER)) {
				if (parts.length == 2 && parts[1].toLowerCase().endsWith(".ogg")) {
					sounds.add(new String[]{stripExt(parts[1], ".ogg"), sha});
					shaToPath.put(sha, path);
				}
				continue;
			}

			if (folder.equals(BACKGROUND_FOLDER)) {
				if (parts.length == 2 && parts[1].toLowerCase().endsWith(".png")) {
					backgrounds.add(new String[]{stripExt(parts[1], ".png"), sha});
					shaToPath.put(sha, path);
				}
				continue;
			}

			Categories.Category cat = Categories.byId(folder);
			if (cat == null) continue;
			TreeMap<String, Option> opts = byCategory.computeIfAbsent(cat.id(), k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));

			if (parts.length == 2) {
				// Einzelne Datei: MeinName.png (+ optional MeinName.png.mcmeta fuer Animationen)
				String file = parts[1];
				String lower = file.toLowerCase();
				if (lower.endsWith(".png")) {
					opts.computeIfAbsent(stripExt(file, ".png"), Option::new).files.put(cat.mainKey(), sha);
					shaToPath.put(sha, path);
				} else if (lower.endsWith(".png.mcmeta")) {
					opts.computeIfAbsent(stripExt(file, ".png.mcmeta"), Option::new).files.put(cat.mainKey() + ".mcmeta", sha);
					shaToPath.put(sha, path);
				}
			} else if (parts.length == 3) {
				// Ordner-Option: MeinName/<dateiname aus Categories>
				String key = parts[2].toLowerCase();
				String baseKey = key.endsWith(".mcmeta") ? key.substring(0, key.length() - ".mcmeta".length()) : key;
				if (cat.files().containsKey(baseKey)) {
					opts.computeIfAbsent(parts[1], Option::new).files.put(key, sha);
					shaToPath.put(sha, path);
				} else {
					GauchosGallery.LOGGER.warn("Unbekannte Datei {} – erlaubt sind: {}", path, cat.files().keySet());
				}
			}
		}

		// 3) Fehlende Dateien herunterladen (bereits geladene liegen im Cache)
		List<String> missing = new ArrayList<>();
		for (String sha : shaToPath.keySet()) {
			if (!Files.exists(cacheFile(sha))) missing.add(sha);
		}
		if (!missing.isEmpty()) {
			if (offline) throw new IOException("offline und " + missing.size() + " Dateien fehlen im Cache");
			AtomicInteger done = new AtomicInteger();
			ExecutorService pool = Executors.newFixedThreadPool(6);
			try {
				List<CompletableFuture<Void>> jobs = new ArrayList<>();
				for (String sha : missing) {
					jobs.add(CompletableFuture.runAsync(() -> {
						try {
							download(shaToPath.get(sha), sha);
						} catch (Exception e) {
							throw new RuntimeException("Download fehlgeschlagen: " + shaToPath.get(sha) + " – " + e.getMessage(), e);
						}
						status = "Lade Dateien … " + done.incrementAndGet() + "/" + missing.size();
					}, pool));
				}
				CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).join();
			} finally {
				pool.shutdown();
			}
		}

		// 4) Snapshot bauen, Vorschau-Indizes vergeben
		Map<String, List<Option>> result = new LinkedHashMap<>();
		int previewIndex = 0;
		int optionCount = 0;
		for (Categories.Category cat : Categories.all()) {
			TreeMap<String, Option> opts = byCategory.get(cat.id());
			if (opts == null) continue;
			List<Option> list = new ArrayList<>();
			for (Option o : opts.values()) {
				if (o.files.keySet().stream().noneMatch(k -> k.endsWith(".png"))) continue; // nur mcmeta, keine PNG
				String previewKey = o.files.containsKey(cat.mainKey()) ? cat.mainKey()
						: o.files.keySet().stream().filter(k -> k.endsWith(".png")).findFirst().orElse(null);
				if (previewKey != null) {
					o.previewSha = o.files.get(previewKey);
					o.previewIndex = previewIndex++;
				}
				list.add(o);
			}
			if (!list.isEmpty()) {
				result.put(cat.id(), List.copyOf(list));
				optionCount += list.size();
			}
		}
		sounds.sort(Comparator.comparing(s -> s[0].toLowerCase()));
		List<KillSound> soundList = new ArrayList<>();
		for (int i = 0; i < sounds.size(); i++) {
			soundList.add(new KillSound(sounds.get(i)[0], sounds.get(i)[1], i));
		}

		backgrounds.sort(Comparator.comparing(b -> b[0].toLowerCase()));
		List<Background> bgList = new ArrayList<>();
		for (String[] b : backgrounds) {
			int[] size = pngSize(cacheFile(b[1]));
			if (size == null) {
				GauchosGallery.LOGGER.warn("Hintergrund {} ist keine gueltige PNG", b[0]);
				continue;
			}
			bgList.add(new Background(b[0], b[1], bgList.size(), size[0], size[1]));
		}

		status = (offline ? "Offline – " : "") + optionCount + " Texturen · " + soundList.size() + " Sounds · "
				+ bgList.size() + " Hintergründe";
		return new Snapshot(result, List.copyOf(soundList), List.copyOf(bgList));
	}

	private void download(String path, String sha) throws IOException, InterruptedException {
		StringBuilder encPath = new StringBuilder();
		for (String seg : path.split("/")) {
			if (!encPath.isEmpty()) encPath.append('/');
			encPath.append(enc(seg));
		}
		String url = "https://raw.githubusercontent.com/" + repo + "/" + enc(branch) + "/" + encPath;
		Path tmp = cacheDir.resolve(sha + ".part");
		HttpResponse<Path> res = http.send(request(url).build(), HttpResponse.BodyHandlers.ofFile(tmp));
		if (res.statusCode() != 200) {
			Files.deleteIfExists(tmp);
			throw new IOException("HTTP " + res.statusCode());
		}
		Files.move(tmp, cacheFile(sha), StandardCopyOption.REPLACE_EXISTING);
	}

	/** Liest Breite/Hoehe aus dem PNG-Header (ohne das Bild zu laden). */
	private static int[] pngSize(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] h = in.readNBytes(24);
			if (h.length < 24 || (h[0] & 0xFF) != 0x89 || h[1] != 'P' || h[2] != 'N' || h[3] != 'G') return null;
			int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
			int ht = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
			return (w > 0 && ht > 0) ? new int[]{w, ht} : null;
		} catch (IOException e) {
			return null;
		}
	}

	private static HttpRequest.Builder request(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", "GauchosTextureGallery");
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String stripExt(String name, String ext) {
		return name.substring(0, name.length() - ext.length());
	}
}
