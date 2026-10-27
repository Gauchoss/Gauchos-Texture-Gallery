package de.gaucho.gallery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Was der Spieler ausgewaehlt hat. Liegt in .minecraft/config/gauchos_gallery.json */
public class GalleryConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("gauchos_gallery.json");

	/** Kategorie-ID -> Name der gewaehlten Option. Fehlt der Eintrag = Vanilla-Textur. */
	public Map<String, String> selections = new LinkedHashMap<>();
	/** Name des Kill-Sounds, null = kein Sound. */
	public String killSound = null;
	/** 0.0 - 1.0 */
	public float killSoundVolume = 1.0f;

	// ---- Aussehen des Menues (jeder Spieler fuer sich) ----
	/** "dark" oder "light" */
	public String theme = "dark";
	/** Name der Akzentfarbe, siehe Theme.ACCENTS */
	public String accent = "Gold";
	/** null = automatisch (erstes Bild aus der Galerie), "-" = kein Bild, sonst Name des Bildes */
	public String background = null;
	/** Wie stark das Hintergrundbild abgedunkelt/aufgehellt wird, 0-80 % */
	public int backgroundDim = 40;

	public static GalleryConfig load() {
		if (Files.exists(FILE)) {
			try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				GalleryConfig cfg = GSON.fromJson(r, GalleryConfig.class);
				if (cfg != null) {
					if (cfg.selections == null) cfg.selections = new LinkedHashMap<>();
					return cfg;
				}
			} catch (Exception e) {
				GauchosGallery.LOGGER.warn("Konnte Config nicht lesen, nutze Standard", e);
			}
		}
		return new GalleryConfig();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			GauchosGallery.LOGGER.error("Konnte Config nicht speichern", e);
		}
	}
}
