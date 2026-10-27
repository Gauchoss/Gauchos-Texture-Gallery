package de.gaucho.gallery;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Das Ingame-Menue (Standardtaste: G). */
public class GalleryScreen extends Screen {
	private static final String KILL_SOUND = "__kill_sound";
	private static final int LEFT_X = 8;
	private static final int LEFT_W = 160;
	private static final int ROW_H = 22;
	private static final int TOP = 36;
	private static final int BOTTOM_BAR = 32;
	private static final int TILE_W = 76;
	private static final int TILE_H = 68;
	private static final int PREVIEW = 40;
	private static final float[] VOLUMES = {0.25f, 0.5f, 0.75f, 1.0f};

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int GOLD = 0xFFFFC94A;
	private static final int GREEN = 0xFF55FF55;

	// Merkt sich die zuletzt offene Kategorie zwischen zwei Oeffnungen
	private static String lastCategory = KILL_SOUND;

	private final Screen parent;
	private final Map<String, String> pending;
	private String pendingSound;
	private float pendingVolume;
	private String selected = lastCategory;
	private int leftScroll = 0;
	private int rightScroll = 0;
	private int seenVersion = -1;
	private int tickCounter = 0;

	private int rightX;
	private int rightW;
	private final List<Header> headers = new ArrayList<>();
	private final List<Tile> tiles = new ArrayList<>();
	private final Set<Integer> loadedPreviews = new HashSet<>();
	private String rightTitle = "";
	private String rightHint = null;

	private record Header(String text, int y) {}

	private record Tile(int x, int y, GalleryRepository.Option option, boolean selected) {}

	public GalleryScreen(Screen parent) {
		super(Component.literal("Gauchos Texture Gallery"));
		this.parent = parent;
		GalleryConfig cfg = GauchosGallery.config();
		this.pending = new LinkedHashMap<>(cfg.selections);
		this.pendingSound = cfg.killSound;
		this.pendingVolume = cfg.killSoundVolume;
	}

	// ------------------------------------------------------------ Aufbau

	@Override
	protected void init() {
		seenVersion = GauchosGallery.dataVersion();
		headers.clear();
		tiles.clear();
		rightHint = null;
		rightX = LEFT_X + LEFT_W + 12;
		rightW = Math.max(TILE_W, this.width - rightX - 8);

		buildLeftList();
		if (KILL_SOUND.equals(selected)) {
			buildSoundPanel();
		} else {
			Categories.Category cat = Categories.byId(selected);
			if (cat == null) {
				selected = KILL_SOUND;
				buildSoundPanel();
			} else {
				buildTilePanel(cat);
			}
		}
		refreshPreviewState();

		int by = this.height - 26;
		int bw = 120;
		int bx = this.width / 2 - (bw * 3 + 8) / 2;
		Button reload = this.addRenderableWidget(Button.builder(Component.literal("Galerie neu laden"), b -> {
			GauchosGallery.reloadGallery();
		}).bounds(bx, by, bw, 20).tooltip(Tooltip.create(Component.literal("Holt neue Texturen & Sounds von GitHub"))).build());
		reload.active = !GauchosGallery.repository().isLoading();
		Button applyBtn = this.addRenderableWidget(Button.builder(Component.literal("Übernehmen"), b -> apply())
				.bounds(bx + bw + 4, by, bw, 20).build());
		applyBtn.active = isDirty();
		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose()).bounds(bx + (bw + 4) * 2, by, bw, 20).build());
	}

	private List<Object> leftRows() {
		List<Object> rows = new ArrayList<>();
		rows.add(KILL_SOUND);
		for (Categories.Group g : Categories.GROUPS) {
			rows.add(g);
			for (Categories.Category c : Categories.all()) {
				if (c.group() == g) rows.add(c);
			}
		}
		return rows;
	}

	private void buildLeftList() {
		List<Object> rows = leftRows();
		int visible = Math.max(1, (this.height - TOP - BOTTOM_BAR) / ROW_H);
		leftScroll = clamp(leftScroll, 0, Math.max(0, rows.size() - visible));
		GalleryRepository.Snapshot snap = GauchosGallery.repository().snapshot();

		for (int i = 0; i < visible; i++) {
			int idx = leftScroll + i;
			if (idx >= rows.size()) break;
			int y = TOP + i * ROW_H;
			Object row = rows.get(idx);
			if (row instanceof Categories.Group g) {
				headers.add(new Header(g.label(), y + 7));
			} else if (row instanceof Categories.Category c) {
				int count = snap.optionsFor(c.id()).size();
				MutableComponent label = Component.literal((c.id().equals(selected) ? "» " : "") + c.label());
				if (pending.containsKey(c.id())) {
					label.append(Component.literal(" ✔").withStyle(ChatFormatting.GREEN));
				} else if (count > 0) {
					label.append(Component.literal(" (" + count + ")").withStyle(ChatFormatting.GRAY));
				}
				this.addRenderableWidget(Button.builder(label, b -> select(c.id())).bounds(LEFT_X, y, LEFT_W, 20).build());
			} else {
				MutableComponent label = Component.literal((KILL_SOUND.equals(selected) ? "» " : "") + "♪ Kill-Sound");
				if (pendingSound != null) label.append(Component.literal(" ✔").withStyle(ChatFormatting.GREEN));
				this.addRenderableWidget(Button.builder(label, b -> select(KILL_SOUND)).bounds(LEFT_X, y, LEFT_W, 20).build());
			}
		}
	}

	private void buildTilePanel(Categories.Category cat) {
		rightTitle = cat.label();
		List<GalleryRepository.Option> opts = GauchosGallery.repository().snapshot().optionsFor(cat.id());
		List<GalleryRepository.Option> entries = new ArrayList<>();
		entries.add(null); // "Standard" = Vanilla
		entries.addAll(opts);
		if (opts.isEmpty()) {
			rightHint = "Noch nichts hochgeladen. Ordner im Repo: gallery/" + cat.id() + "/";
		}

		int cols = Math.max(1, rightW / TILE_W);
		int top = TOP + 14;
		int visibleRows = Math.max(1, (this.height - top - BOTTOM_BAR) / TILE_H);
		int totalRows = (entries.size() + cols - 1) / cols;
		rightScroll = clamp(rightScroll, 0, Math.max(0, totalRows - visibleRows));

		String current = pending.get(cat.id());
		for (int i = 0; i < visibleRows * cols; i++) {
			int idx = rightScroll * cols + i;
			if (idx >= entries.size()) break;
			int tx = rightX + (i % cols) * TILE_W;
			int ty = top + (i / cols) * TILE_H;
			GalleryRepository.Option o = entries.get(idx);
			String name = o == null ? null : o.name;
			boolean isSel = Objects.equals(current, name);
			tiles.add(new Tile(tx, ty, o, isSel));

			Button b = Button.builder(Component.literal(o == null ? "Standard" : o.name), btn -> {
				if (name == null) pending.remove(cat.id());
				else pending.put(cat.id(), name);
				this.rebuildWidgets();
			}).bounds(tx, ty + PREVIEW + 6, TILE_W - 4, 20)
					.tooltip(Tooltip.create(Component.literal(o == null ? "Originale Minecraft-Textur" : o.name)))
					.build();
			this.addRenderableWidget(b);
		}
	}

	private void buildSoundPanel() {
		rightTitle = "Kill-Sound";
		List<GalleryRepository.KillSound> sounds = GauchosGallery.repository().snapshot().killSounds();

		int y0 = TOP + 14;
		this.addRenderableWidget(Button.builder(Component.literal("Lautstärke: " + Math.round(pendingVolume * 100) + "%"), b -> {
			int next = 0;
			for (int i = 0; i < VOLUMES.length; i++) {
				if (Math.abs(VOLUMES[i] - pendingVolume) < 0.01f) next = (i + 1) % VOLUMES.length;
			}
			pendingVolume = VOLUMES[next];
			this.rebuildWidgets();
		}).bounds(rightX, y0, 120, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("▶ Testen"), b -> {
			GalleryRepository.KillSound s = GauchosGallery.repository().snapshot().findSound(pendingSound);
			if (s != null) KillTracker.playSound(s, pendingVolume);
		}).bounds(rightX + 124, y0, 80, 20).build());

		if (sounds.isEmpty()) {
			rightHint = "Noch keine Sounds. Lade .ogg-Dateien nach gallery/killsounds/ hoch.";
		}

		List<GalleryRepository.KillSound> entries = new ArrayList<>();
		entries.add(null);
		entries.addAll(sounds);
		int listTop = y0 + 28;
		int visible = Math.max(1, (this.height - listTop - BOTTOM_BAR) / 24);
		rightScroll = clamp(rightScroll, 0, Math.max(0, entries.size() - visible));
		int rowW = Math.min(rightW, 260);

		for (int i = 0; i < visible; i++) {
			int idx = rightScroll + i;
			if (idx >= entries.size()) break;
			GalleryRepository.KillSound s = entries.get(idx);
			String name = s == null ? null : s.name;
			boolean isSel = Objects.equals(pendingSound, name);
			int y = listTop + i * 24;
			MutableComponent label = Component.literal((isSel ? "» " : "") + (s == null ? "Kein Sound" : s.name));
			if (isSel) label.withStyle(ChatFormatting.GREEN);
			this.addRenderableWidget(Button.builder(label, b -> {
				pendingSound = name;
				this.rebuildWidgets();
			}).bounds(rightX, y, rowW - 26, 20).build());
			if (s != null) {
				this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> KillTracker.playSound(s, pendingVolume))
						.bounds(rightX + rowW - 22, y, 22, 20)
						.tooltip(Tooltip.create(Component.literal("Anhören")))
						.build());
			}
		}
	}

	// ------------------------------------------------------------ Logik

	private void select(String id) {
		if (!id.equals(selected)) {
			selected = id;
			lastCategory = id;
			rightScroll = 0;
		}
		this.rebuildWidgets();
	}

	private boolean isDirty() {
		GalleryConfig cfg = GauchosGallery.config();
		return !pending.equals(cfg.selections)
				|| !Objects.equals(pendingSound, cfg.killSound)
				|| Math.abs(pendingVolume - cfg.killSoundVolume) > 0.001f;
	}

	private void apply() {
		if (!isDirty()) return;
		GalleryConfig cfg = GauchosGallery.config();
		boolean texturesChanged = !pending.equals(cfg.selections);
		cfg.selections = new LinkedHashMap<>(pending);
		cfg.killSound = pendingSound;
		cfg.killSoundVolume = pendingVolume;
		cfg.save();
		if (texturesChanged) GauchosGallery.applySelection();
		this.rebuildWidgets();
	}

	private Identifier previewId(int index) {
		return Identifier.fromNamespaceAndPath(PackBuilder.NAMESPACE, PackBuilder.previewPath(index));
	}

	/** Prueft, welche Vorschaubilder schon im geladenen Resource Pack stecken. */
	private void refreshPreviewState() {
		if (this.minecraft == null) return;
		for (Tile t : tiles) {
			if (t.option() == null || t.option().previewIndex < 0) continue;
			int idx = t.option().previewIndex;
			if (!loadedPreviews.contains(idx)
					&& this.minecraft.getResourceManager().getResource(previewId(idx)).isPresent()) {
				loadedPreviews.add(idx);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (seenVersion != GauchosGallery.dataVersion()) {
			loadedPreviews.clear();
			this.rebuildWidgets();
		} else if (++tickCounter % 20 == 0) {
			refreshPreviewState();
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
		if (delta == 0) return false;
		if (mouseX < rightX - 6) leftScroll += delta;
		else rightScroll += delta;
		this.rebuildWidgets();
		return true;
	}

	@Override
	public void onClose() {
		apply(); // auch mit ESC wird gespeichert
		if (this.minecraft != null) this.minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------ Zeichnen

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Der Hintergrund wird von Minecraft (1.21.6+) automatisch vorher gezeichnet.
		// Rahmen + Vorschauen zuerst, damit die Buttons darueber liegen
		for (Tile t : tiles) {
			int x = t.x();
			int y = t.y();
			int w = TILE_W - 4;
			int h = PREVIEW + 4;
			g.fill(x, y, x + w, y + h, 0x66000000);
			if (t.selected()) {
				outline(g, x - 1, y - 1, w + 2, h + 2 + 22, GREEN);
			}
			int px = x + (w - PREVIEW) / 2;
			int py = y + 2;
			GalleryRepository.Option o = t.option();
			if (o == null) {
				g.drawCenteredString(this.font, "Vanilla", x + w / 2, y + h / 2 - 4, GRAY);
			} else if (o.previewIndex >= 0 && loadedPreviews.contains(o.previewIndex)) {
				g.blit(RenderPipelines.GUI_TEXTURED, previewId(o.previewIndex), px, py, 0f, 0f, PREVIEW, PREVIEW, PREVIEW, PREVIEW);
			} else {
				g.drawCenteredString(this.font, "…", x + w / 2, y + h / 2 - 4, GRAY);
			}
		}

		super.render(g, mouseX, mouseY, delta);

		g.drawCenteredString(this.font, this.title, this.width / 2, 8, GOLD);
		String status = GauchosGallery.repository().status();
		g.drawCenteredString(this.font, status, this.width / 2, 20, GauchosGallery.repository().isLoading() ? WHITE : GRAY);

		for (Header h : headers) {
			g.drawString(this.font, h.text(), LEFT_X + 2, h.y(), GOLD);
		}
		g.drawString(this.font, rightTitle, rightX, TOP + 2, WHITE);
		if (rightHint != null) {
			int hy = KILL_SOUND.equals(selected) ? this.height - BOTTOM_BAR - 14 : TOP + 14 + TILE_H + 4;
			g.drawString(this.font, rightHint, rightX, hy, GRAY);
		}
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
