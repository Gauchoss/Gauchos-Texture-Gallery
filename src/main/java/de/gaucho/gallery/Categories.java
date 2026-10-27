package de.gaucho.gallery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alle Dinge, deren Textur man in der Galerie aendern kann.
 *
 * Jede Kategorie entspricht einem Ordner in "gallery/" im GitHub-Repo.
 * Eine Option ist entweder
 *   - eine einzelne PNG  (gallery/&lt;kategorie&gt;/MeinName.png)  -> ersetzt die Haupttextur, oder
 *   - ein Ordner         (gallery/&lt;kategorie&gt;/MeinName/...)   -> Dateien mit den unten definierten Namen.
 *
 * Alle Zielpfade sind relativ zu assets/minecraft/textures/ (Stand Minecraft 1.21.11).
 */
public final class Categories {

	public record Group(String id, String label) {}

	/**
	 * @param files Dateiname im Options-Ordner -> Liste von Vanilla-Texturpfaden.
	 *              Der ERSTE Eintrag ist die Haupttextur (wird auch fuer Einzel-PNGs benutzt).
	 */
	public record Category(String id, Group group, String label, LinkedHashMap<String, List<String>> files) {
		public String mainKey() {
			return files.keySet().iterator().next();
		}

		public List<String> mainTargets() {
			return files.get(mainKey());
		}
	}

	public static final Group PVP = new Group("pvp", "PvP-Items");
	public static final Group BLOCKS = new Group("blocks", "Blöcke");
	public static final Group WEAPONS = new Group("weapons", "Waffen & Werkzeuge");
	public static final Group ARMOR = new Group("armor", "Rüstung & Elytra");

	public static final List<Group> GROUPS = List.of(PVP, BLOCKS, WEAPONS, ARMOR);

	private static final List<Category> ALL = new ArrayList<>();
	private static final Map<String, Category> BY_ID = new LinkedHashMap<>();

	// Materialien: id im Spiel, Anzeigename
	private static final String[][] TOOL_MATERIALS = {
			{"wooden", "Holz"}, {"stone", "Stein"}, {"copper", "Kupfer"}, {"iron", "Eisen"},
			{"golden", "Gold"}, {"diamond", "Diamant"}, {"netherite", "Netherit"}
	};
	// item-Praefix, equipment-Texturname, Anzeigename
	private static final String[][] ARMOR_MATERIALS = {
			{"leather", "leather", "Leder"}, {"chainmail", "chainmail", "Ketten"}, {"copper", "copper", "Kupfer"},
			{"iron", "iron", "Eisen"}, {"golden", "gold", "Gold"}, {"diamond", "diamond", "Diamant"},
			{"netherite", "netherite", "Netherit"}
	};

	static {
		// ---------- PvP ----------
		add(PVP, "golden_apple", "Goldener Apfel", f("golden_apple.png", "item/golden_apple.png"));
		add(PVP, "end_crystal", "End-Kristall (Item)", f("end_crystal.png", "item/end_crystal.png"));
		add(PVP, "end_crystal_placed", "End-Kristall (platziert)", f("end_crystal.png", "entity/end_crystal/end_crystal.png"));
		add(PVP, "totem", "Totem der Unsterblichkeit", f("totem.png", "item/totem_of_undying.png"));
		add(PVP, "ender_pearl", "Enderperle", f("ender_pearl.png", "item/ender_pearl.png"));
		add(PVP, "water_bucket", "Wassereimer", f("water_bucket.png", "item/water_bucket.png"));

		// ---------- Bloecke ----------
		add(BLOCKS, "obsidian", "Obsidian", f("obsidian.png", "block/obsidian.png"));
		add(BLOCKS, "respawn_anchor", "Seelenanker", f(
				"side.png", List.of("block/respawn_anchor_side0.png", "block/respawn_anchor_side1.png",
						"block/respawn_anchor_side2.png", "block/respawn_anchor_side3.png", "block/respawn_anchor_side4.png"),
				"side0.png", List.of("block/respawn_anchor_side0.png"),
				"side1.png", List.of("block/respawn_anchor_side1.png"),
				"side2.png", List.of("block/respawn_anchor_side2.png"),
				"side3.png", List.of("block/respawn_anchor_side3.png"),
				"side4.png", List.of("block/respawn_anchor_side4.png"),
				"top.png", List.of("block/respawn_anchor_top.png"),
				"top_off.png", List.of("block/respawn_anchor_top_off.png"),
				"bottom.png", List.of("block/respawn_anchor_bottom.png")));
		add(BLOCKS, "glowstone", "Leuchtstein", f("glowstone.png", "block/glowstone.png"));
		add(BLOCKS, "cobweb", "Spinnennetz", f("cobweb.png", "block/cobweb.png"));

		// ---------- Waffen & Werkzeuge ----------
		for (String[] m : TOOL_MATERIALS) {
			add(WEAPONS, m[0] + "_sword", m[1] + "schwert", f(m[0] + "_sword.png", "item/" + m[0] + "_sword.png"));
		}
		for (String[] m : TOOL_MATERIALS) {
			add(WEAPONS, m[0] + "_pickaxe", m[1] + "spitzhacke", f(m[0] + "_pickaxe.png", "item/" + m[0] + "_pickaxe.png"));
		}
		for (String[] m : TOOL_MATERIALS) {
			add(WEAPONS, m[0] + "_axe", m[1] + "axt", f(m[0] + "_axe.png", "item/" + m[0] + "_axe.png"));
		}
		add(WEAPONS, "bow", "Bogen", f(
				"bow.png", List.of("item/bow.png"),
				"pulling_0.png", List.of("item/bow_pulling_0.png"),
				"pulling_1.png", List.of("item/bow_pulling_1.png"),
				"pulling_2.png", List.of("item/bow_pulling_2.png")));
		add(WEAPONS, "crossbow", "Armbrust", f(
				"crossbow.png", List.of("item/crossbow_standby.png"),
				"pulling_0.png", List.of("item/crossbow_pulling_0.png"),
				"pulling_1.png", List.of("item/crossbow_pulling_1.png"),
				"pulling_2.png", List.of("item/crossbow_pulling_2.png"),
				"arrow.png", List.of("item/crossbow_arrow.png"),
				"firework.png", List.of("item/crossbow_firework.png")));
		add(WEAPONS, "trident", "Dreizack", f(
				"trident.png", List.of("item/trident.png"),
				"3d.png", List.of("entity/trident.png")));
		add(WEAPONS, "shield", "Schild", f(
				"shield.png", List.of("entity/shield_base_nopattern.png", "entity/shield_base.png")));

		// ---------- Ruestung ----------
		for (String[] m : ARMOR_MATERIALS) {
			add(ARMOR, m[0] + "_helmet", m[2] + "-Helm", f(m[0] + "_helmet.png", "item/" + m[0] + "_helmet.png"));
			add(ARMOR, m[0] + "_chestplate", m[2] + "-Brustpanzer", f(m[0] + "_chestplate.png", "item/" + m[0] + "_chestplate.png"));
			add(ARMOR, m[0] + "_leggings", m[2] + "-Hose", f(m[0] + "_leggings.png", "item/" + m[0] + "_leggings.png"));
			add(ARMOR, m[0] + "_boots", m[2] + "-Stiefel", f(m[0] + "_boots.png", "item/" + m[0] + "_boots.png"));
			add(ARMOR, m[0] + "_armor_worn", m[2] + "-Rüstung (getragen)", f(
					"layer_1.png", List.of("entity/equipment/humanoid/" + m[1] + ".png"),
					"layer_2.png", List.of("entity/equipment/humanoid_leggings/" + m[1] + ".png")));
		}
		add(ARMOR, "elytra", "Elytra", f(
				"elytra.png", List.of("entity/equipment/wings/elytra.png"),
				"item.png", List.of("item/elytra.png"),
				"broken.png", List.of("item/elytra_broken.png")));
	}

	private Categories() {}

	public static List<Category> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static Category byId(String id) {
		return BY_ID.get(id);
	}

	private static void add(Group group, String id, String label, LinkedHashMap<String, List<String>> files) {
		Category c = new Category(id, group, label, files);
		ALL.add(c);
		BY_ID.put(id, c);
	}

	private static LinkedHashMap<String, List<String>> f(String key, String target) {
		LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
		map.put(key, List.of(target));
		return map;
	}

	@SuppressWarnings("unchecked")
	private static LinkedHashMap<String, List<String>> f(Object... keyAndTargets) {
		LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
		for (int i = 0; i < keyAndTargets.length; i += 2) {
			map.put((String) keyAndTargets[i], (List<String>) keyAndTargets[i + 1]);
		}
		return map;
	}
}
