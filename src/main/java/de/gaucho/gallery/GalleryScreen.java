package de.gaucho.gallery;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Das Ingame-Menue (Standardtaste: G). Komplett eigenes, flaches Design mit Themes. */
public class GalleryScreen extends Screen {
	private static final String PAGE_SOUND = "__kill_sound";
	private static final String PAGE_SETTINGS = "__settings";

	private static final int HEADER = 30;
	private static final int SIDEBAR = 150;
	private static final int ROW = 16;
	private static final int TILE = 40;
	private static final int GAP = 6;
	private static final int PAD = 12;
	private static final float[] VOLUMES = {0.25f, 0.5f, 0.75f, 1.0f};
	private static final int[] DIMS = {0, 20, 40, 60, 80};

	private static String lastPage = PAGE_SOUND;

	private final Screen parent;
	private final Map<String, String> pending;
	private String pendingSound;
	private float pendingVolume;
	private String page = lastPage;
	private int leftScroll = 0;
	private int rightScroll = 0;
	private int seenVersion = -1;
	private int tickCounter = 0;

	// Layout (wird in init() berechnet)
	private Theme t;
	private int px, py, pw, ph;
	private int cx, cy, cw, ch;
	private final List<Label> labels = new ArrayList<>();
	/** Welche Bilder (Vorschauen/Hintergruende) schon im geladenen Resource Pack sind. */
	private final Map<Identifier, Boolean> loaded = new HashMap<>();

	/** Text, der in render() gezeichnet wird (Ueberschriften, Hinweise). */
	private record Label(String text, int x, int y, int color) {}

	public GalleryScreen(Screen parent) {
		super(Component.literal("Gauchos Texture Gallery"));
		this.parent = parent;
		GalleryConfig cfg = GauchosGallery.config();
		this.pending = new LinkedHashMap<>(cfg.selections);
		this.pendingSound = cfg.killSound;
		this.pendingVolume = cfg.killSoundVolume;
	}

	// =============================================================== Aufbau

	@Override
	protected void init() {
		seenVersion = GauchosGallery.dataVersion();
		t = Theme.current();
		labels.clear();

		pw = Math.min(this.width - 20, 640);
		ph = Math.min(this.height - 20, 380);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		cx = px + SIDEBAR + PAD;
		cy = py + HEADER + 10;
		cw = px + pw - PAD - cx;
		ch = py + ph - 10 - cy;

		buildHeader();
		buildSidebar();
		if (PAGE_SETTINGS.equals(page)) {
			buildSettings();
		} else if (PAGE_SOUND.equals(page) || Categories.byId(page) == null) {
			page = PAGE_SOUND;
			buildSounds();
		} else {
			buildTiles(Categories.byId(page));
		}
	}

	private void buildHeader() {
		int size = 18;
		int y = py + (HEADER - size) / 2;
		int x = px + pw - 8 - size;

		add(new UiButton(x, y, size, size, "Schließen", (g, b, hov) -> iconButton(g, b, hov, "✕", false), this::onClose))
				.tooltip("Schließen (speichert automatisch)");
		x -= size + 4;
		add(new UiButton(x, y, size, size, "Einstellungen",
				(g, b, hov) -> iconButton(g, b, hov, "⚙", PAGE_SETTINGS.equals(page)), () -> select(PAGE_SETTINGS)))
				.tooltip("Aussehen");
		x -= size + 4;
		UiButton reload = add(new UiButton(x, y, size, size, "Neu laden",
				(g, b, hov) -> iconButton(g, b, hov, "↻", false), GauchosGallery::reloadGallery))
				.tooltip("Galerie neu von GitHub laden");
		reload.active = !GauchosGallery.repository().isLoading();

		if (isDirty()) {
			String txt = "Übernehmen";
			int w = this.font.width(txt) + 16;
			x -= w + 8;
			add(new UiButton(x, y, w, size, txt, (g, b, hov) -> {
				g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(),
						hov ? Theme.alpha(t.accent(), 0xDD) : t.accent());
				centered(g, txt, b.getX() + b.getWidth() / 2, b.getY() + 5, t.onAccent());
			}, this::apply)).tooltip("Texturen jetzt anwenden");
		}
	}

	private List<Object> sidebarRows() {
		List<Object> rows = new ArrayList<>();
		rows.add(PAGE_SOUND);
		for (Categories.Group grp : Categories.GROUPS) {
			rows.add(grp);
			for (Categories.Category c : Categories.all()) {
				if (c.group() == grp) rows.add(c);
			}
		}
		return rows;
	}

	private void buildSidebar() {
		List<Object> rows = sidebarRows();
		int top = py + HEADER + 4;
		int visible = Math.max(1, (py + ph - 4 - top) / ROW);
		leftScroll = clamp(leftScroll, 0, Math.max(0, rows.size() - visible));
		GalleryRepository.Snapshot snap = GauchosGallery.repository().snapshot();
		int x = px + 6;
		int w = SIDEBAR - 12;

		for (int i = 0; i < visible; i++) {
			int idx = leftScroll + i;
			if (idx >= rows.size()) break;
			int y = top + i * ROW;
			Object row = rows.get(idx);
			if (row instanceof Categories.Group grp) {
				labels.add(new Label(grp.label().toUpperCase(), x + 4, y + 6, t.textMuted()));
			} else if (row instanceof Categories.Category c) {
				int count = snap.optionsFor(c.id()).size();
				add(new UiButton(x, y, w, ROW, c.label(), (g, b, hov) ->
						sidebarItem(g, b, hov, c.label(), c.id().equals(page), pending.containsKey(c.id()), count), () -> select(c.id())));
			} else {
				add(new UiButton(x, y, w, ROW, "Kill-Sound", (g, b, hov) ->
						sidebarItem(g, b, hov, "♪  Kill-Sound", PAGE_SOUND.equals(page), pendingSound != null, -1), () -> select(PAGE_SOUND)));
			}
		}
	}

	private void buildTiles(Categories.Category cat) {
		List<GalleryRepository.Option> opts = GauchosGallery.repository().snapshot().optionsFor(cat.id());
		labels.add(new Label(cat.label(), cx, cy, t.text()));
		labels.add(new Label(opts.size() + (opts.size() == 1 ? " Variante" : " Varianten"),
				cx + this.font.width(cat.label()) + 8, cy, t.textMuted()));

		List<GalleryRepository.Option> entries = new ArrayList<>();
		entries.add(null); // Standard
		entries.addAll(opts);

		int top = cy + 18;
		int cols = Math.max(1, (cw + GAP) / (TILE + GAP));
		int visibleRows = Math.max(1, (cy + ch - top + GAP) / (TILE + GAP));
		int totalRows = (entries.size() + cols - 1) / cols;
		rightScroll = clamp(rightScroll, 0, Math.max(0, totalRows - visibleRows));

		String current = pending.get(cat.id());
		for (int i = 0; i < visibleRows * cols; i++) {
			int idx = rightScroll * cols + i;
			if (idx >= entries.size()) break;
			GalleryRepository.Option o = entries.get(idx);
			String name = o == null ? null : o.name;
			boolean sel = Objects.equals(current, name);
			int x = cx + (i % cols) * (TILE + GAP);
			int y = top + (i / cols) * (TILE + GAP);
			Identifier img = (o != null && o.previewIndex >= 0) ? previewId(o.previewIndex) : null;
			add(new UiButton(x, y, TILE, TILE, o == null ? "Standard" : o.name, (g, b, hov) -> {
				box(g, b, hov ? t.surfaceHover() : t.surface());
				if (o == null) {
					centered(g, "↺", b.getX() + TILE / 2, b.getY() + TILE / 2 - 4, t.textMuted());
				} else if (img != null && isLoaded(img)) {
					g.blit(RenderPipelines.GUI_TEXTURED, img, b.getX() + 4, b.getY() + 4, 0f, 0f, TILE - 8, TILE - 8, TILE - 8, TILE - 8);
				} else {
					centered(g, "…", b.getX() + TILE / 2, b.getY() + TILE / 2 - 4, t.textMuted());
				}
				if (sel) outline(g, b.getX(), b.getY(), TILE, TILE, 2, t.accent());
			}, () -> {
				if (name == null) pending.remove(cat.id());
				else pending.put(cat.id(), name);
				this.rebuildWidgets();
			})).tooltip(o == null ? "Standard (Original-Textur)" : o.name);
		}

		if (opts.isEmpty()) {
			int y = top + TILE + 12;
			labels.add(new Label("Noch keine Texturen hochgeladen.", cx, y, t.textMuted()));
			labels.add(new Label("Ordner im Repo: gallery/" + cat.id() + "/", cx, y + 12, t.textMuted()));
		}
	}

	private void buildSounds() {
		List<GalleryRepository.KillSound> sounds = GauchosGallery.repository().snapshot().killSounds();
		labels.add(new Label("Kill-Sound", cx, cy, t.text()));

		int y = cy + 18;
		labels.add(new Label("Lautstärke", cx, y + 4, t.textMuted()));
		int x = cx + 64;
		for (float v : VOLUMES) {
			String txt = Math.round(v * 100) + "%";
			boolean sel = Math.abs(v - pendingVolume) < 0.01f;
			int w = 36;
			add(new UiButton(x, y, w, 16, txt, (g, b, hov) -> pill(g, b, hov, txt, sel), () -> {
				pendingVolume = v;
				this.rebuildWidgets();
			}));
			x += w + 4;
		}

		List<GalleryRepository.KillSound> entries = new ArrayList<>();
		entries.add(null);
		entries.addAll(sounds);
		int top = y + 26;
		int rowH = 18;
		int visible = Math.max(1, (cy + ch - top) / (rowH + 3));
		rightScroll = clamp(rightScroll, 0, Math.max(0, entries.size() - visible));
		int rowW = Math.min(cw, 300);

		for (int i = 0; i < visible; i++) {
			int idx = rightScroll + i;
			if (idx >= entries.size()) break;
			GalleryRepository.KillSound s = entries.get(idx);
			String name = s == null ? null : s.name;
			String label = s == null ? "Kein Sound" : s.name;
			boolean sel = Objects.equals(pendingSound, name);
			int ry = top + i * (rowH + 3);
			int w = s == null ? rowW : rowW - rowH - 3;
			add(new UiButton(cx, ry, w, rowH, label, (g, b, hov) -> {
				box(g, b, sel ? Theme.alpha(t.accent(), 0x40) : (hov ? t.surfaceHover() : t.surface()));
				if (sel) g.fill(b.getX(), b.getY(), b.getX() + 2, b.getY() + b.getHeight(), t.accent());
				text(g, fit(label, b.getWidth() - 16), b.getX() + 8, b.getY() + 5, sel ? t.text() : t.text());
			}, () -> {
				pendingSound = name;
				if (s != null) KillTracker.playSound(s, pendingVolume);
				this.rebuildWidgets();
			}));
			if (s != null) {
				add(new UiButton(cx + rowW - rowH, ry, rowH, rowH, "Anhören", (g, b, hov) -> {
					box(g, b, hov ? t.surfaceHover() : t.surface());
					centered(g, "▶", b.getX() + b.getWidth() / 2 + 1, b.getY() + 5, hov ? t.accent() : t.textMuted());
				}, () -> KillTracker.playSound(s, pendingVolume))).tooltip("Anhören");
			}
		}

		if (sounds.isEmpty()) {
			labels.add(new Label("Noch keine Sounds. Ordner: gallery/killsounds/ (.ogg)", cx, top + rowH + 10, t.textMuted()));
		}
	}

	private void buildSettings() {
		GalleryConfig cfg = GauchosGallery.config();
		labels.add(new Label("Aussehen", cx, cy, t.text()));
		int y = cy + 20;
		int lx = cx;
		int vx = cx + 80;

		// Design
		labels.add(new Label("Design", lx, y + 4, t.textMuted()));
		int x = vx;
		for (String[] opt : new String[][]{{"dark", "Dunkel"}, {"light", "Hell"}}) {
			boolean sel = opt[0].equals(cfg.theme) || (!"light".equals(cfg.theme) && opt[0].equals("dark"));
			int w = this.font.width(opt[1]) + 16;
			add(new UiButton(x, y, w, 16, opt[1], (g, b, hov) -> pill(g, b, hov, opt[1], sel), () -> {
				cfg.theme = opt[0];
				saveSettings();
			}));
			x += w + 4;
		}

		// Akzentfarbe
		y += 26;
		labels.add(new Label("Farbe", lx, y + 4, t.textMuted()));
		x = vx;
		for (Map.Entry<String, Integer> e : Theme.ACCENTS.entrySet()) {
			String name = e.getKey();
			int color = e.getValue();
			boolean sel = name.equals(cfg.accent) || (!Theme.ACCENTS.containsKey(cfg.accent) && name.equals("Gold"));
			if (x + 16 > cx + cw) break;
			add(new UiButton(x, y, 16, 16, name, (g, b, hov) -> {
				int bx = b.getX(), by = b.getY();
				if (sel || hov) outline(g, bx, by, 16, 16, 1, sel ? t.text() : t.textMuted());
				g.fill(bx + 2, by + 2, bx + 14, by + 14, color);
			}, () -> {
				cfg.accent = name;
				saveSettings();
			})).tooltip(name);
			x += 20;
		}

		// Hintergrundbild
		y += 26;
		labels.add(new Label("Hintergrund", lx, y + 4, t.textMuted()));
		List<GalleryRepository.Background> bgs = GauchosGallery.repository().snapshot().backgrounds();
		GalleryRepository.Background active = effectiveBackground();
		int thumbW = 64, thumbH = 36;
		x = vx;
		int rowY = y;
		List<GalleryRepository.Background> entries = new ArrayList<>();
		entries.add(null);
		entries.addAll(bgs);
		for (GalleryRepository.Background bg : entries) {
			if (x + thumbW > cx + cw) {
				x = vx;
				rowY += thumbH + GAP;
			}
			boolean sel = Objects.equals(active, bg);
			Identifier img = bg == null ? null : backgroundId(bg.index);
			add(new UiButton(x, rowY, thumbW, thumbH, bg == null ? "Kein Bild" : bg.name, (g, b, hov) -> {
				box(g, b, hov ? t.surfaceHover() : t.surface());
				if (bg == null) {
					centered(g, "Kein Bild", b.getX() + thumbW / 2, b.getY() + thumbH / 2 - 4, t.textMuted());
				} else if (isLoaded(img)) {
					blitCover(g, img, b.getX() + 2, b.getY() + 2, thumbW - 4, thumbH - 4, bg.width, bg.height);
				} else {
					centered(g, "…", b.getX() + thumbW / 2, b.getY() + thumbH / 2 - 4, t.textMuted());
				}
				if (sel) outline(g, b.getX(), b.getY(), thumbW, thumbH, 2, t.accent());
			}, () -> {
				cfg.background = bg == null ? "-" : bg.name;
				saveSettings();
			})).tooltip(bg == null ? "Ohne Hintergrundbild" : bg.name);
			x += thumbW + GAP;
		}
		y = rowY + thumbH;
		if (bgs.isEmpty()) {
			labels.add(new Label("Bilder kommen aus gallery/backgrounds/", vx + thumbW + GAP, y - thumbH + 14, t.textMuted()));
		}

		// Abdunkeln
		y += 12;
		labels.add(new Label(t.dark() ? "Abdunkeln" : "Aufhellen", lx, y + 4, t.textMuted()));
		x = vx;
		for (int d : DIMS) {
			String txt = d + "%";
			boolean sel = cfg.backgroundDim == d;
			int w = 36;
			add(new UiButton(x, y, w, 16, txt, (g, b, hov) -> pill(g, b, hov, txt, sel), () -> {
				cfg.backgroundDim = d;
				saveSettings();
			}));
			x += w + 4;
		}

		labels.add(new Label("Diese Einstellungen gelten nur für dich.", cx, y + 30, t.textMuted()));
	}

	// =============================================================== Logik

	private <T extends UiButton> T add(T w) {
		return this.addRenderableWidget(w);
	}

	private void select(String id) {
		if (!id.equals(page)) {
			page = id;
			if (!PAGE_SETTINGS.equals(id)) lastPage = id;
			rightScroll = 0;
		}
		this.rebuildWidgets();
	}

	private void saveSettings() {
		GauchosGallery.config().save();
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

	/** null = kein Bild. */
	private GalleryRepository.Background effectiveBackground() {
		GalleryConfig cfg = GauchosGallery.config();
		List<GalleryRepository.Background> bgs = GauchosGallery.repository().snapshot().backgrounds();
		if ("-".equals(cfg.background)) return null;
		GalleryRepository.Background b = GauchosGallery.repository().snapshot().findBackground(cfg.background);
		if (b != null) return b;
		return bgs.isEmpty() ? null : bgs.get(0); // automatisch: erstes Bild
	}

	private static Identifier previewId(int index) {
		return Identifier.fromNamespaceAndPath(PackBuilder.NAMESPACE, PackBuilder.previewPath(index));
	}

	private static Identifier backgroundId(int index) {
		return Identifier.fromNamespaceAndPath(PackBuilder.NAMESPACE, PackBuilder.backgroundPath(index));
	}

	/** Ist das Bild schon im geladenen Resource Pack? (Ergebnis wird gemerkt, Fehlschlaege jede Sekunde neu geprueft.) */
	private boolean isLoaded(Identifier id) {
		Boolean known = loaded.get(id);
		if (known != null) return known;
		boolean present = this.minecraft != null && this.minecraft.getResourceManager().getResource(id).isPresent();
		loaded.put(id, present);
		return present;
	}

	@Override
	public void tick() {
		super.tick();
		if (seenVersion != GauchosGallery.dataVersion()) {
			loaded.clear();
			this.rebuildWidgets();
		} else if (++tickCounter % 20 == 0) {
			loaded.values().removeIf(present -> !present);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int delta = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
		if (delta == 0) return false;
		if (mouseX < px + SIDEBAR) leftScroll += delta;
		else rightScroll += delta;
		this.rebuildWidgets();
		return true;
	}

	@Override
	public void onClose() {
		apply(); // auch mit ESC wird gespeichert
		if (this.minecraft != null) this.minecraft.setScreen(parent);
	}

	// =============================================================== Zeichnen

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		if (t == null) t = Theme.current();
		GalleryRepository.Background bg = effectiveBackground();
		Identifier id = bg == null ? null : backgroundId(bg.index);
		if (id != null && isLoaded(id)) {
			blitCover(g, id, 0, 0, this.width, this.height, bg.width, bg.height);
			int dim = GauchosGallery.config().backgroundDim * 255 / 100;
			if (dim > 0) g.fill(0, 0, this.width, this.height, Theme.alpha(t.dark() ? 0x000000 : 0xFFFFFF, dim));
		} else {
			super.renderBackground(g, mouseX, mouseY, delta);
			g.fill(0, 0, this.width, this.height, t.overlay());
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		// Fenster
		g.fill(px, py, px + pw, py + ph, t.panel());
		g.fill(px, py + HEADER, px + SIDEBAR, py + ph, t.sidebar());
		g.fill(px, py + HEADER - 1, px + pw, py + HEADER, t.border());
		g.fill(px + SIDEBAR - 1, py + HEADER, px + SIDEBAR, py + ph, t.border());
		g.fill(px, py, px + pw, py + 2, t.accent()); // Akzent-Linie oben

		// Titel
		int tx = px + 12;
		int ty = py + (HEADER - 8) / 2 + 1;
		text(g, "Gauchos", tx, ty, t.accent());
		text(g, " Texture Gallery", tx + this.font.width("Gauchos"), ty, t.text());

		// Status (rechts neben dem Titel, falls Platz)
		int titleEnd = tx + this.font.width("Gauchos Texture Gallery") + 16;
		int statusEnd = firstHeaderButtonX() - 10;
		if (statusEnd - titleEnd > 40) {
			boolean loading = GauchosGallery.repository().isLoading();
			String status = fit((loading ? "● " : "") + GauchosGallery.repository().status(), statusEnd - titleEnd);
			text(g, status, statusEnd - this.font.width(status), ty, loading ? t.accent() : t.textMuted());
		}

		for (Label l : labels) text(g, l.text(), l.x(), l.y(), l.color());

		super.render(g, mouseX, mouseY, delta);
	}

	private int firstHeaderButtonX() {
		int x = px + pw - 8 - 18 * 3 - 8;
		if (isDirty()) x -= this.font.width("Übernehmen") + 16 + 8;
		return x;
	}

	// ---- kleine Zeichen-Helfer ----

	private void sidebarItem(GuiGraphics g, UiButton b, boolean hov, String label, boolean sel, boolean chosen, int count) {
		int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
		if (sel) {
			g.fill(x, y, x + w, y + h, Theme.alpha(t.accent(), 0x38));
			g.fill(x, y + 2, x + 2, y + h - 2, t.accent());
		} else if (hov) {
			g.fill(x, y, x + w, y + h, t.surfaceHover());
		}
		int right = x + w - 6;
		if (chosen) {
			g.fill(right - 4, y + 6, right, y + 10, t.accent());
			right -= 10;
		} else if (count > 0) {
			String c = String.valueOf(count);
			text(g, c, right - this.font.width(c), y + 4, t.textMuted());
			right -= this.font.width(c) + 6;
		}
		text(g, fit(label, right - (x + 8)), x + 8, y + 4, sel ? t.text() : (count == 0 ? t.textMuted() : t.text()));
	}

	private void iconButton(GuiGraphics g, UiButton b, boolean hov, String glyph, boolean sel) {
		if (sel) box(g, b, Theme.alpha(t.accent(), 0x55));
		else if (hov) box(g, b, t.surfaceHover());
		int color = !b.active ? Theme.alpha(t.textMuted(), 0x80) : (sel || hov ? t.text() : t.textMuted());
		centered(g, glyph, b.getX() + b.getWidth() / 2, b.getY() + (b.getHeight() - 8) / 2, color);
	}

	private void pill(GuiGraphics g, UiButton b, boolean hov, String txt, boolean sel) {
		box(g, b, sel ? t.accent() : (hov ? t.surfaceHover() : t.surface()));
		centered(g, txt, b.getX() + b.getWidth() / 2, b.getY() + (b.getHeight() - 8) / 2, sel ? t.onAccent() : t.text());
	}

	private static void box(GuiGraphics g, UiButton b, int color) {
		g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), color);
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int thick, int color) {
		g.fill(x, y, x + w, y + thick, color);
		g.fill(x, y + h - thick, x + w, y + h, color);
		g.fill(x, y + thick, x + thick, y + h - thick, color);
		g.fill(x + w - thick, y + thick, x + w, y + h - thick, color);
	}

	/** Bild so skalieren, dass es die Flaeche ausfuellt (Raender werden abgeschnitten). */
	private static void blitCover(GuiGraphics g, Identifier id, int x, int y, int w, int h, int texW, int texH) {
		float scale = Math.max((float) w / texW, (float) h / texH);
		int regionW = Math.max(1, Math.min(texW, Math.round(w / scale)));
		int regionH = Math.max(1, Math.min(texH, Math.round(h / scale)));
		float u = (texW - regionW) / 2f;
		float v = (texH - regionH) / 2f;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, texW, texH);
	}

	private void text(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(this.font, s, x, y, color, false);
	}

	private void centered(GuiGraphics g, String s, int centerX, int y, int color) {
		g.drawString(this.font, s, centerX - this.font.width(s) / 2, y, color, false);
	}

	/** Kuerzt Text mit "…", damit er in die Breite passt. */
	private String fit(String s, int maxW) {
		if (maxW <= 0) return "";
		if (this.font.width(s) <= maxW) return s;
		return this.font.plainSubstrByWidth(s, Math.max(0, maxW - this.font.width("…"))) + "…";
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
