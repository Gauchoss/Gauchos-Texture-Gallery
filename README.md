# Gauchos Texture Gallery

Fabric-Mod für **Minecraft 1.21.11**. Ingame-Menü (Taste **G**), in dem man für PvP-Items,
Schwerter, Spitzhacken, Äxte, Rüstung, Elytra, Bogen, Armbrust, Dreizack, Schild, Totem,
Enderperle, Wassereimer, Obsidian, Seelenanker, Leuchtstein und Spinnennetz eine Textur aus
**deiner Galerie** auswählt – plus einen eigenen **Kill-Sound**.

- Texturen und Sounds liegen im Ordner [`gallery/`](gallery/README.md) dieses GitHub-Repos.
- Nur du (Besitzer des Repos) kannst dort hochladen. Deine Freunde laden nur herunter.
- Neue Texturen brauchen **keine neue Mod-Version** – einfach hochladen, ingame „Galerie neu laden“.

---

## 1. Einmalig einrichten (ca. 10 Minuten)

1. Auf <https://github.com> einloggen (oder Konto erstellen).
2. Oben rechts **+ → New repository**
   - Name: `gauchos-texture-gallery`
   - **Public** auswählen (sonst können deine Freunde die Texturen nicht laden)
   - **Create repository**
3. Auf der neuen Seite auf **„uploading an existing file“** klicken und **alle Dateien und
   Ordner aus diesem Projekt** hineinziehen (auch den Ordner `.github` – der ist auf manchen
   PCs versteckt; unter Windows: Explorer → Ansicht → „Ausgeblendete Elemente“ anhaken).
   Dann **Commit changes**.
4. Oben auf den Tab **Actions** klicken. Falls gefragt: „I understand my workflows, go ahead and
   enable them“. Der Build läuft jetzt automatisch (2–5 Minuten). Falls nichts startet:
   links **build** → **Run workflow**.
5. Wenn er grün ist: rechts auf der Repo-Startseite unter **Releases** liegt
   `gauchos-texture-gallery-1.0.X.jar`. **Das ist deine Mod.**

> Dein Repo-Name wird beim Bauen automatisch in die Mod eingetragen – du musst keinen Code anfassen.

## 2. Mod installieren (du und deine Freunde)

1. [Fabric Loader](https://fabricmc.net/use/installer/) für **1.21.11** installieren.
2. **Fabric API** für 1.21.11 (z. B. von Modrinth) in den `mods`-Ordner legen.
3. Die `gauchos-texture-gallery-….jar` in den `mods`-Ordner legen.
4. Spiel starten, **G** drücken (änderbar unter Optionen → Steuerung).

Schick deinen Freunden einfach den Link zu den Releases oder die .jar-Datei.

## 3. Texturen & Sounds hochladen

Siehe [`gallery/README.md`](gallery/README.md) – dort steht für jedes Item der Ordnername.
Kurzfassung: PNG in z. B. `gallery/golden_apple/` hochladen, fertig.
Zwei Beispiele (pinker Apfel, Ding-Sound) sind schon drin – kannst du löschen.

## So funktioniert's (technisch)

- Beim Start liest die Mod die Dateiliste deines Repos und lädt neue Dateien in
  `.minecraft/config/gauchos_gallery_cache/` (danach geht's auch offline).
- Aus der Auswahl baut sie ein normales Resource Pack `resourcepacks/GauchosTextureGallery`
  und aktiviert es ganz oben. Deshalb funktioniert alles auch mit Sodium/Iris.
- Kill-Sound: wird nur bei dir abgespielt. Erkannt werden Spieler, die du geschlagen hast,
  oder die neben einem End-Kristall / Seelenanker standen, den du ausgelöst hast, und
  danach sterben. Funktioniert auf jedem Server, nichts muss auf dem Server installiert sein.

## Selbst bauen (optional)

`gradle.properties` → `gallery_repo=DEINNAME/gauchos-texture-gallery`, dann `./gradlew build`
(Java 21+). Die Jar liegt in `build/libs/`.
