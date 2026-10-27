# Galerie – hier lädst du Texturen & Kill-Sounds hoch

Alles in diesem Ordner taucht automatisch bei allen Spielern mit der Mod auf
(beim Spielstart oder über **„Galerie neu laden“** im Menü). Nur du kannst hier
hochladen – deine Freunde können nur auswählen.

## So geht's

1. Öffne auf GitHub den passenden Ordner (Tabelle unten), z. B. `gallery/golden_apple/`.
   Gibt es den Ordner noch nicht: **Add file → Create new file** und als Namen
   `golden_apple/platzhalter.txt` eintippen – damit ist der Ordner angelegt.
2. **Add file → Upload files** → PNG reinziehen → **Commit changes**.
3. Der **Dateiname ist der Name im Menü**: `Feuer Apfel.png` erscheint als „Feuer Apfel“.

**Einfach:** eine PNG direkt in den Ordner → ersetzt die Haupttextur.
**Fortgeschritten:** ein Unterordner (z. B. `gallery/bow/Mein Bogen/`) mit den Dateinamen aus
der rechten Spalte → so kannst du mehrere Texturen auf einmal ändern (z. B. Bogen beim Spannen).

**Tipps**
- Item-Texturen sind normalerweise 16×16 Pixel (größer geht auch, z. B. 32×32 oder 64×64).
- Für getragene Rüstung und Elytra nimm die Original-Datei als Vorlage (gleiche Größe/Aufteilung).
- Animation: lege eine `Name.png.mcmeta` daneben (wie in normalen Resource Packs).

## Kill-Sounds

Ordner `gallery/killsounds/` – Dateien müssen **.ogg** sein (Ogg Vorbis, am besten mono).
MP3/WAV kannst du z. B. mit Audacity oder einem Online-Converter in .ogg umwandeln.
Kurz halten (unter 2 Sekunden klingt am besten).

## Hintergrundbilder fürs Menü

Ordner `gallery/backgrounds/` – ganz normale **.png**-Bilder (z. B. 1920×1080 oder 1280×720).
Das Bild wird automatisch so skaliert, dass es den Bildschirm füllt.
Lädst du mehrere hoch, kann sich jeder Spieler unter **⚙ → Hintergrund** eins aussuchen.
Ohne Auswahl wird automatisch das erste (alphabetisch) benutzt.

## Alle Ordner

### PvP-Items

| Ordner | Was | Dateinamen für Ordner-Optionen (optional) |
|---|---|---|
| `golden_apple` | Goldener Apfel | – |
| `end_crystal` | End-Kristall (Item) | – |
| `end_crystal_placed` | End-Kristall (platziert) | – |
| `totem` | Totem der Unsterblichkeit | – |
| `ender_pearl` | Enderperle | – |
| `water_bucket` | Wassereimer | – |

### Blöcke

| Ordner | Was | Dateinamen für Ordner-Optionen (optional) |
|---|---|---|
| `obsidian` | Obsidian | – |
| `respawn_anchor` | Seelenanker | `side.png`, `side0.png`, `side1.png`, `side2.png`, `side3.png`, `side4.png`, `top.png`, `top_off.png`, `bottom.png` |
| `glowstone` | Leuchtstein | – |
| `cobweb` | Spinnennetz | – |

### Waffen & Werkzeuge

| Ordner | Was | Dateinamen für Ordner-Optionen (optional) |
|---|---|---|
| `wooden_sword` | Holzschwert | – |
| `stone_sword` | Steinschwert | – |
| `copper_sword` | Kupferschwert | – |
| `iron_sword` | Eisenschwert | – |
| `golden_sword` | Goldschwert | – |
| `diamond_sword` | Diamantschwert | – |
| `netherite_sword` | Netheritschwert | – |
| `wooden_pickaxe` | Holzspitzhacke | – |
| `stone_pickaxe` | Steinspitzhacke | – |
| `copper_pickaxe` | Kupferspitzhacke | – |
| `iron_pickaxe` | Eisenspitzhacke | – |
| `golden_pickaxe` | Goldspitzhacke | – |
| `diamond_pickaxe` | Diamantspitzhacke | – |
| `netherite_pickaxe` | Netheritspitzhacke | – |
| `wooden_axe` | Holzaxt | – |
| `stone_axe` | Steinaxt | – |
| `copper_axe` | Kupferaxt | – |
| `iron_axe` | Eisenaxt | – |
| `golden_axe` | Goldaxt | – |
| `diamond_axe` | Diamantaxt | – |
| `netherite_axe` | Netheritaxt | – |
| `bow` | Bogen | `bow.png`, `pulling_0.png`, `pulling_1.png`, `pulling_2.png` |
| `crossbow` | Armbrust | `crossbow.png`, `pulling_0.png`, `pulling_1.png`, `pulling_2.png`, `arrow.png`, `firework.png` |
| `trident` | Dreizack | `trident.png`, `3d.png` |
| `shield` | Schild | – |

### Rüstung & Elytra

| Ordner | Was | Dateinamen für Ordner-Optionen (optional) |
|---|---|---|
| `leather_helmet` | Leder-Helm | – |
| `leather_chestplate` | Leder-Brustpanzer | – |
| `leather_leggings` | Leder-Hose | – |
| `leather_boots` | Leder-Stiefel | – |
| `leather_armor_worn` | Leder-Rüstung (getragen) | `layer_1.png`, `layer_2.png` |
| `chainmail_helmet` | Ketten-Helm | – |
| `chainmail_chestplate` | Ketten-Brustpanzer | – |
| `chainmail_leggings` | Ketten-Hose | – |
| `chainmail_boots` | Ketten-Stiefel | – |
| `chainmail_armor_worn` | Ketten-Rüstung (getragen) | `layer_1.png`, `layer_2.png` |
| `copper_helmet` | Kupfer-Helm | – |
| `copper_chestplate` | Kupfer-Brustpanzer | – |
| `copper_leggings` | Kupfer-Hose | – |
| `copper_boots` | Kupfer-Stiefel | – |
| `copper_armor_worn` | Kupfer-Rüstung (getragen) | `layer_1.png`, `layer_2.png` |
| `iron_helmet` | Eisen-Helm | – |
| `iron_chestplate` | Eisen-Brustpanzer | – |
| `iron_leggings` | Eisen-Hose | – |
| `iron_boots` | Eisen-Stiefel | – |
| `iron_armor_worn` | Eisen-Rüstung (getragen) | `layer_1.png`, `layer_2.png` |
| `golden_helmet` | Gold-Helm | – |
| `golden_chestplate` | Gold-Brustpanzer | – |
| `golden_leggings` | Gold-Hose | – |
| `golden_boots` | Gold-Stiefel | – |
| `golden_armor_worn` | Gold-Rüstung (getragen) | `layer_1.png`, `layer_2.png` |
| `diamond_helmet` | Diamant-Helm | – |
| `diamond_chestplate` | Diamant-Brustpanzer | – |
| `diamond_leggings` | Diamant-Hose | – |
| `diamond_boots` | Diamant-Stiefel | – |
| `diamond_armor_worn` | Diamant-Rüstung (getragen) | `layer_1.png`, `layer_2.png` |
| `netherite_helmet` | Netherit-Helm | – |
| `netherite_chestplate` | Netherit-Brustpanzer | – |
| `netherite_leggings` | Netherit-Hose | – |
| `netherite_boots` | Netherit-Stiefel | – |
| `netherite_armor_worn` | Netherit-Rüstung (getragen) | `layer_1.png`, `layer_2.png` |
| `elytra` | Elytra | `elytra.png`, `item.png`, `broken.png` |
