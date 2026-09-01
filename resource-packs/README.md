# DreamCraft Resource Packs

This directory contains source files and local tooling for DreamCraft resource
packs. It intentionally contains no placeholder textures, models, sounds, or
fake assets.

## Layout

```text
resource-packs/
  packs/
    dreamcraft/
      assets/
        dreamcraft/
          font/
            gothic.json
            gui.json
          models/
            item/
              city/
                icon.json
              estate/
                icon.json
              menu/
                back.json
                close.json
                deposit.json
                profile.json
              nucleus/
                icon.json
              ward/
                *.json
          sounds/
          textures/
            font/
              gui/
                bg_9.png … bg_54.png
            item/
              city/
                icon.png
              estate/
                icon.png
              menu/
                back.png
                close.png
                deposit.png
                profile.png
              nucleus/
                icon.png
              ward/
                *.png
        minecraft/
          items/
            arrow.json
            beacon.json
            book.json
            chest.json
            cracked_stone_bricks.json
            experience_bottle.json
            lime_stained_glass_pane.json
            nether_star.json
            paper.json
            shield.json
  licenses/
    OldEnglishGothicPixel/
  scripts/
    build.ps1
    clean.ps1
    validate.ps1
  dist/
```

- `font/gui.json` declares the `dreamcraft:gui` font: six full-menu background
  glyphs (`\uE100`–`\uE105` → `textures/font/gui/bg_9..54.png`, ascent 13) plus
  custom spacing characters for menu layout. Background PNGs are 176 px wide
  and `22 + rows × 18` px tall (40…130): an 18 px brand header, slot rows on
  the vanilla grid (first row at y = 18) and a 4 px bottom margin.
- `font/gothic.json` registers the Old English Gothic Pixel decorative font
  (`gothic.ttf`, TTF provider, size 10 / oversample 8 — tune in-game if
  needed). The OFL license and author readme ship in
  `licenses/OldEnglishGothicPixel/`: the TTF is redistributed together with
  its license files, as the OFL requires.
- `items/paper.json` remaps PAPER via `custom_model_data` range dispatch:
  CMD 41403 → `models/item/menu/profile.json` (texture
  `textures/item/menu/profile.png`), CMD 41404 →
  `models/item/menu/close.json` (texture `textures/item/menu/close.png`).

## Source And Build Output

- Source lives under `resource-packs/packs/`:
  - `packs/dreamcraft/`: Java Edition resource pack (`assets/dreamcraft/`, `assets/minecraft/`, `pack.mcmeta`, `pack.png`).
  - `packs/dreamcraft-bedrock/`: Bedrock Edition resource pack (`manifest.json`, `pack_icon.png`, `textures/`, `blocks.json`, `font/`).
- Generated archives live under `resource-packs/dist/`:
  - `dreamcraft-resource-pack.zip`: Java Edition archive (hash `31e5e1012e25c0eb04eee115bcb970567f5ba7dc`).
  - `dreamcraft-bedrock.mcpack`: Bedrock Edition archive.
- `dist/` is ignored by Git except for `.gitkeep`.

The intended build outputs are:

```text
resource-packs/dist/dreamcraft-resource-pack.zip
resource-packs/dist/dreamcraft-bedrock.mcpack
```

## pack.mcmeta (Java)

`pack.mcmeta` exists with `pack_format` 84.

The Docker config declares `VERSION: 26.1.2` / `PAPER_BUILD: 74`. The server jar
(`data/versions/26.1.2/paper-26.1.2.jar`, `version.json`) reports:

```text
pack_version.resource_major = 84 (minor 0)
```

If the server version changes, re-check `version.json` in the new jar and update
`pack.mcmeta`.

## Bedrock Edition Pack Structure

The Bedrock pack (`packs/dreamcraft-bedrock/`) adapts assets from the Java pack for Bedrock clients:
- `manifest.json`: declares the resource module with `format_version: 2`, RFC4122 v4 UUIDs, and `min_engine_version: [1, 21, 0]`.
- `pack_icon.png`: 64×64 brand icon derived from `server-icon.png`.
- `textures/items/`: 81 item textures organized in `city/`, `estate/`, `menu/` (including 2×2 quadrant tiles `q/`), `nucleus/`, and `ward/`.
- `textures/item_texture.json`: Bedrock item atlas defining texture shortnames for all custom items.
- `textures/blocks/` & `textures/terrain_texture.json` & `blocks.json`: Bedrock block atlas and definitions for the custom Nucleus block (`nucleus_face_active`, `nucleus_face_inactive`).
- `font/glyph_E1.png` & `font/glyph_EC.png`: glyph definition sheets for Unicode blocks `E1` and `EC` to prevent broken character boxes (`[?]` / `???`) on Bedrock clients.

## Geyser Custom Mappings

Located in `plugin-configs/Geyser-Spigot/custom_mappings/` (synced to server via `config-sync`):
- `dreamcraft_mappings.json`: maps 89 Java `CustomModelData` entries across 21 vanilla items (Paper, Shield, Beacon, Book, Emerald, etc.) to Bedrock custom item identifiers and atlas icons (`format_version: 2`).
- `dreamcraft_blocks.json`: maps Java `minecraft:note_block` state overrides (`instrument=flute, note=14/15`) to the Bedrock `nucleus_block` (`format_version: 1`).

## Scripts

Run from the repository root:

```powershell
.\resource-packs\scripts\validate.ps1
.\resource-packs\scripts\build.ps1
.\resource-packs\scripts\build-bedrock.ps1
.\resource-packs\scripts\clean.ps1
```

- `validate.ps1` and `build.ps1` intentionally fail if `pack.mcmeta` or referenced textures/models are missing.
- `build-bedrock.ps1` validates `manifest.json` and `pack_icon.png` fail-fast, then packages `dist/dreamcraft-bedrock.mcpack` with forward-slash paths.

## Production Distribution

### Java Edition
- Hosted via HTTP pack server (`http://<host>:8081/dreamcraft-resource-pack-31e5e101.zip`) or GitHub Releases.
- Configured in `server.properties`:
  - `resource-pack`: points to the HTTP/Release download URL.
  - `resource-pack-sha1`: `31e5e1012e25c0eb04eee115bcb970567f5ba7dc`.

### Bedrock Edition
- Geyser automatically distributes `dreamcraft-bedrock.mcpack` from `plugin-configs/Geyser-Spigot/packs/` (or via remote URL in `resource-pack-urls`).
- `plugin-configs/Geyser-Spigot/config.yml` settings:
  - `gameplay.enable-custom-content: true`
  - `force-resource-packs: true`
  - `enable-integrated-pack: true`

