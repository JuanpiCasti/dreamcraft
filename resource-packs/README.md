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

- Source lives under `resource-packs/packs/`.
- Generated archives live under `resource-packs/dist/`.
- `dist/` is ignored by Git except for `.gitkeep`.

The intended build output is:

```text
resource-packs/dist/dreamcraft-resource-pack.zip
```

## pack.mcmeta

`pack.mcmeta` exists with `pack_format` 84.

The Docker config declares `VERSION: 26.1.2` / `PAPER_BUILD: 74`. The server jar
(`data/versions/26.1.2/paper-26.1.2.jar`, `version.json`) reports:

```text
pack_version.resource_major = 84 (minor 0)
```

If the server version changes, re-check `version.json` in the new jar and update
`pack.mcmeta`.

## Scripts

Run from the repository root:

```powershell
.\resource-packs\scripts\validate.ps1
.\resource-packs\scripts\build.ps1
.\resource-packs\scripts\clean.ps1
```

`validate.ps1` and `build.ps1` intentionally fail if `pack.mcmeta` is missing.
This prevents producing a resource pack with an uncertain format.
