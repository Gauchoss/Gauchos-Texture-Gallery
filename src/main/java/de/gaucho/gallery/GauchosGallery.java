package de.gaucho.gallery;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public class GauchosGallery implements ClientModInitializer {
	public static final String MOD_ID = "gauchos_gallery";
	public static final Logger LOGGER = LoggerFactory.getLogger("GauchosTextureGallery");

	private static GalleryConfig config;
	private static GalleryRepository repository;
	private static final KillTracker KILL_TRACKER = new KillTracker();
	private static KeyMapping openKey;
	/** Wird hochgezaehlt, wenn neue Galerie-Daten oder Vorschaubilder da sind (Menue baut sich dann neu auf). */
	private static volatile int dataVersion = 0;

	@Override
	public void onInitializeClient() {
		config = GalleryConfig.load();
		repository = new GalleryRepository();
		KILL_TRACKER.register();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.gauchos_gallery.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) {
				if (mc.screen == null) mc.setScreen(new GalleryScreen(null));
			}
			KILL_TRACKER.tick(mc);
		});

		// Beim Spielstart Galerie von GitHub laden und Pack anwenden
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> reloadGallery());
	}

	public static GalleryConfig config() {
		return config;
	}

	public static GalleryRepository repository() {
		return repository;
	}

	public static int dataVersion() {
		return dataVersion;
	}

	/** Holt die neueste Galerie von GitHub und wendet die Auswahl an. */
	public static void reloadGallery() {
		CompletableFuture<GalleryRepository.Snapshot> f = repository.refresh();
		if (f == null) return; // laeuft schon
		f.thenAccept(snap -> {
			dataVersion++;
			applySelection();
		});
	}

	/** Baut den Resource Pack aus der aktuellen Auswahl und laedt die Texturen neu. */
	public static CompletableFuture<Void> applySelection() {
		Minecraft mc = Minecraft.getInstance();
		Executor renderThread = mc::execute;
		GalleryRepository.Snapshot snap = repository.snapshot();
		Map<String, String> selections = new LinkedHashMap<>(config.selections);
		return CompletableFuture.supplyAsync(() -> {
			try {
				return PackBuilder.build(snap, selections, repository);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		}).thenAcceptAsync(changed -> {
			PackBuilder.apply(mc, changed);
			dataVersion++;
		}, renderThread).exceptionally(e -> {
			LOGGER.error("Resource Pack konnte nicht gebaut werden", e);
			return null;
		});
	}
}
