# Recursos gráficos · Contrato de nombres con el diseñador

> **Audiencia:** diseñador/a de texturas del Resource Pack y equipo del
> servidor. Este documento fija **qué archivos debe contener** el resource
> pack y **cómo se llaman**, para que plugin y pack encajen sin ensayo y error.
>
> La fuente de verdad técnica es `presentation-assets.yml` (claves → ítem base
> + CustomModelData). Este documento traduce esas claves a **rutas de archivo**
> dentro del resource pack.

---

## 1. Reglas de oro

1. Los **nombres de archivo nunca cambian**: el servidor referencia los CMDs;
   el pack solo provee los modelos/texturas en estas rutas.
2. Agregar texturas nuevas es libre — avisar y sumar fila a las tablas.
3. PNG, minúsculas, sin espacios; resolución recomendada **32�32** (mín. 16�16).
4. Un asset ausente NO rompe nada: el menú cae al material vanilla configurado
   como `fallback`.

---

## 2. Convención de rutas

La clave semántica se convierte en ruta bajo el namespace `dreamcraft`:

```text
clave ward.icon
  ▼
assets/dreamcraft/models/item/ward/icon.json     ← modelo (apunta a la textura)
assets/dreamcraft/textures/item/ward/icon.png    ← TU textura aquí
```

Plantilla del modelo (referencial — el formato final lo decide el pipeline del
pack; en MC 1.21.4+ se declara en `assets/minecraft/items/<item_base>.json`):

```json
// assets/dreamcraft/models/item/ward/icon.json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "dreamcraft:item/ward/icon" }
}
```

---

## 3. Iconos requeridos (menús)

| Clave (`presentation-assets.yml`) | Ítem base | CMD | Archivo de textura requerido |
|---|---|---|---|
| `nucleus.icon` | NOTE_BLOCK | 41002 | `items/note_block.json` (ítem) + `models/block/nucleus_cube[_inactive].json` (colocado, variante mágica) + `textures/item/nucleus/icon.png` + `textures/block/nucleus_face_{active,inactive}.png` |
| `ward.icon` | SHIELD | 41101 | `textures/item/ward/icon.png` |
| `ward.inactive` | CRACKED_STONE_BRICKS | 41102 | `textures/item/ward/inactive.png` |
| `ward.tier` | NETHER_STAR | 41103 | `textures/item/ward/tier.png` |
| `ward.score` | EXPERIENCE_BOTTLE | 41104 | `textures/item/ward/score.png` |
| `ward.upkeep` | CHEST | 41105 | `textures/item/ward/upkeep.png` |
| `ward.orphan` | BARRIER | 41106 | `textures/item/ward/orphan.png` (placeholder barrera magenta) |
| `city.icon` | BEACON | 41201 | `models/item/city/icon.json` + `textures/item/city/icon.png` |
| `city.members` | PLAYER_HEAD | — | *(vanilla, sin archivo)* |
| `city.treasury` | GOLD_BLOCK | 41202 | `models/item/city/treasury.json` + `textures/item/city/treasury.png` |
| `city.score` | EMERALD | 41203 | `models/item/city/score.json` + `textures/item/city/score.png` |
| `city.admin` | COMMAND_BLOCK | 41204 | `textures/item/city/admin.png` (grupo de personas, hoja 06) |
| `estate.icon` | BOOK | 41301 | `models/item/estate/icon.json` + `textures/item/estate/icon.png` |
| `estate.adventure` | ENDER_EYE | 41302 | `models/item/estate/adventure.json` + `textures/item/estate/adventure.png` |
| `estate.instance` | END_PORTAL_FRAME | 41303 | `textures/item/estate/instance.png` (portal, hoja 07) |
| `estate.dragon` | DRAGON_HEAD | 41304 | `models/item/estate/dragon.json` + `textures/item/estate/dragon.png` |
| `estate.zone-tp` | NETHER_STAR | 41305 | `models/item/estate/zone-tp.json` + `textures/item/estate/zone-tp.png` |
| `menu.deposit` | LIME_STAINED_GLASS_PANE | 41401 | `models/item/menu/deposit.json` + `textures/item/menu/deposit.png` |
| `menu.filler` | GRAY_STAINED_GLASS_PANE | — | *(vanilla)* |
| `menu.back` | ARROW | 41402 | `models/item/menu/back.json` + `textures/item/menu/back.png` |
| `menu.profile` | PAPER | 41403 | `models/item/menu/profile.json` + `textures/item/menu/profile.png` (32�32) |
| `menu.close` | PAPER | 41404 | `models/item/menu/close.json` + `textures/item/menu/close.png` (32�32) |
| `menu.invite` | PAPER | 41405 | `models/item/menu/invite.json` + `textures/item/menu/invite.png` (32�32) |
| `menu.kick` | PAPER | 41406 | `models/item/menu/kick.json` + `textures/item/menu/kick.png` (32�32) |
| `menu.roles` | PAPER | 41407 | `models/item/menu/roles.json` + `textures/item/menu/roles.png` (32�32) |
| `menu.members` | PAPER | 41408 | `models/item/menu/members.json` + `textures/item/menu/members.png` (32�32) |
| `menu.confirm` | PAPER | 41409 | `models/item/menu/confirm.json` + `textures/item/menu/confirm.png` (32�32) |

### Botones 2×2 (sistema de cuadrantes · PAPER)

Cada botón grande se compone de **4 slots** que llevan un cuadrante 16×16 del
arte base 32×32 (`tl` arriba-izq, `tr` arriba-der, `bl` abajo-izq, `br`
abajo-der): el plugin aplica los 4 CMDs contiguos y el ítem se ve como una
sola pieza. Modelos: `models/item/menu/q/<grupo>_<cuadrante>.json`
(`item/generated`, layer0); texturas: `textures/item/menu/q/<grupo>_<cuadrante>.png`.

| Grupo (botón) | Arte base (32×32) | CMDs tl/tr/bl/br |
|---|---|---|
| `upkeep` (ward.upkeep) | `textures/item/ward/upkeep.png` | 41501–41504 |
| `fase` ("Elevar Fase", hoy iconKey `ward.tier`) | `textures/item/ward/tier.png` | 41505–41508 |
| `matriz` ("Matriz", hoy icon.city.overview) | `textures/item/city/icon.png` | 41509–41512 |
| `tesoro` (city.treasury) | `textures/item/city/treasury.png` | 41513–41516 |
| `invite` (menu.invite) | `textures/item/menu/invite.png` | 41517–41520 |
| `roles` (menu.roles) | `textures/item/menu/roles.png` | 41521–41524 |
| `iniciar` ("Iniciar" estate lobby, estate.icon) | `textures/item/estate/icon.png` | 41525–41528 |
| `salir` ("Salir" 2×2 estate instance, icon.back) | `textures/item/menu/back.png` | 41529–41532 |
| `ward` (entradas dinámicas admin, ward.icon) | `textures/item/ward/icon.png` | 41533–41536 |
| `inactive` (entradas dinámicas admin apagadas, ward.inactive) | `textures/item/ward/inactive.png` | 41537–41540 |

Todos declarados en `assets/minecraft/items/paper.json` (thresholds ascendentes
después de 41409; fallback `minecraft:item/paper` intacto).

> Nota overflow del glifo GUI (ascent 24): los 6 fondos `bg_9..54` crecieron
> **12 px hacia arriba** (alturas nativas 61/79/97/115/133/151; el borde
> inferior queda clavado) estirando la banda de borde superior del marco, con
> relleno navy opaco y alpha 255 en todo el PNG. Con `"ascent": 24` y baseline
> ≈10, el glifo arranca en y = −14 (12 px por encima del GUI): tapa las
> esquinas grises del borde superior vanilla sin invadir el inventario del
> jugador (el borde inferior sigue cubriendo 25+18·R px con margen).

> Nota: los ítems con identidad especial (SHIELD, PLAYER_HEAD) pueden exigir
> manejo particular en el pipeline del pack; si complica, pedir remapear su
> `material` en `presentation-assets.yml` antes de dibujar.

> Estado de los sprites (hotfix pack):
> - `nucleus.icon` — **ítem base: NOTE_BLOCK con estado mágico**
>   (`instrument=flute,note=14,powered=false`). Ítem (mano/GUI/dropeado):
>   `assets/minecraft/items/note_block.json` (threshold 41002 →
>   `dreamcraft:item/nucleus/icon`, la textura crisp original).
>   **Colocado**: `blockstates/note_block.json` mapea SOLO la variante mágica
>   a `dreamcraft:block/nucleus_cube[_inactive]` (caras de
>   `10-bloque-synt.png`: azul brillante = protegido `powered=false`,
>   gris = desprotegido `powered=true`; el plugin voltea la propiedad vía
>   `WardCoreVisual` en colocación/depósito/tick). Las otras 1049 variantes →
>   note block vanilla. **Lodestone y beacon vanilla: cero overrides.**
>   Atlas (`atlases/blocks.json`): sources `single` explícitas para las
>   texturas usadas por modelos de bloque (directory+namespace NO es válido
>   en vanilla y se ignora silenciosamente).
>   La gótica (`dc.gothic`) aplica por runs: ASCII en gótica, acentos en
>   fuente default (el TTF tiene 84 glifos, sin cobertura de acentos).
> - Fondos de menús **temáticos por dominio** (marcos 9-slice finos de
>   `Dreamcraft-menu-sprites.png`, 10px inferiores transparentes para no
>   invadir el inventario del jugador): synt/acero = `bg_*` (\uE100–\uE105),
>   matriz/azul = `bg_matriz_*` (\uE110–\uE115), nexo/violeta = `bg_nexo_*`
>   (\uE120–\uE125). El compositor elige por prefijo del menuId (los admin
>   heredan: `city_admin_*` → matriz, `estate_admin_*` → nexo).
> - `textures/item/ward/icon.png` e `inactive.png` fueron **restauradas**
>   desde la hoja `brand-data/prompts/05-iconos-ward.png` (las versiones
>   anteriores habían quedado sobrescritas con crops Synt por error y no había
>   historia git que recuperar): escudo azul/violeta con cristal → `icon.png`;
>   escudo gris tachado (la variante apagada ya venía dibujada en la hoja) →
>   `inactive.png`. Método: crop exacto + resize a 32×32 con filtro Catrom,
>   igual que tier/score/upkeep. Ojo: el **escudo dorado alado** de esa hoja
>   (~x1040) NO es el icono — ese ya existe como `textures/item/ward/tier.png`.
> - Arte nuevo (hotfix pack 2): crops 32×32 (recorte exacto + pad cuadrado
>   transparente + resize Catrom) desde las hojas `06-iconos-city.png` y
>   `07-iconos-estate.png`: `city.treasury` (cofre con monedas), `city.score`
>   (trofeo), `estate.adventure` (cartel con camino luminoso),
>   `estate.dragon` (dragón) y `estate.zone-tp` (portal arcoíris).
>   Ojo: la hoja 07 **no trae un icono dedicado a `zone-tp`**; se asignó el
>   portal (que el `.md` describe como "instancia") porque es el símbolo de
>   teletransporte y `zone-tp` es la clave usada por el plugin hoy. Por esa
>   reasignación, `estate.instance` queda **sin arte** (degrada a
>   END_PORTAL_FRAME vanilla) hasta hoja nueva. Overrides declarados en
>   `gold_block.json` (41202), `emerald.json` (41203), `ender_eye.json`
>   (41302), `dragon_head.json` (41304) y `nether_star.json` (41305, junto a
>   41103 ward/tier), todos con fallback vanilla intacto.
> - Botones de menú (hotfix pack 3): crops 32×32 desde la hoja
>   `brand-data/prompts/Dreamcraft-menu-sprites.png` (mismo método: recorte
>   exacto + pad cuadrado + Catrom), eligiendo siempre el estado encendido:
>   `menu.close` = X blanca (~x1041 y366), `menu.kick` = X roja (~x1041 y312,
>   la hoja no trae "persona con X"), `menu.invite` = botón circular "+" azul
>   (~x1304 y313, la hoja no trae "person+"), `menu.roles` = escudo azul
>   (~x1041 y466), `menu.members` = grupo de personas (~x939 y466),
>   `menu.confirm` = check verde circular (~x1150 y415). Quedan intactos
>   `back` y `deposit`.
> - Fondos de menú reconstruidos (hotfix pack 3): los 6 `bg_9..54.png` se
>   regeneraron por **9-slice** desde el marco ornato azul/violeta de la
>   propia hoja `Dreamcraft-menu-sprites.png` (bbox x750..894, y694..855;
>   144×161, esquinas de perilla nativas sin escalar, slice 24 px; bordes
>   estirados y interior con el gradiente navy del centro del marco). Los
>   nuevos tamaños (176×49 ... 176×139) cubren el GUI completo (25+18·R px)
>   con margen de seguridad, eliminando las tiras grises vanilla que se veían
>   con los fondos cortos anteriores. Hotfix pack 4: los 6 fondos llevan
>   **atrás un rectángulo opaco 176×H** relleno con el navy del interior del
>   panel (mediana de los píxeles opacos de la zona interior), compuesto bajo
>   el marco → alpha 255 en todo el PNG (antes las esquinas/perillas
>   semitransparentes dejaban ver el gris vanilla).
> - Fallbacks reparados (hotfix pack 4): desde 1.21.4 Mojang borró los stubs
>   `models/item/*.json` que solo heredaban del bloque, así que todo fallback
>   `minecraft:item/<bloque>` apunta ahora al modelo de bloque:
>   `beacon.json` → `minecraft:block/beacon`, `lodestone.json` →
>   `minecraft:block/lodestone` y `lime_stained_glass_pane.json` →
>   `minecraft:block/lime_stained_glass_pane_post` (el modelo
>   `block/<pane>_post` es el que vanilla usa como ítem; no existe
>   `block/lime_stained_glass_pane`). `gold_block` y
>   `cracked_stone_bricks` ya usaban `block/`; dragon_head/chest/shield usan
>   `special` y quedan intactos.
> - Insurance lodestone colocado (hotfix pack 4): se agregaron
>   `assets/minecraft/blockstates/lodestone.json` (variante `""` →
>   `minecraft:block/lodestone`) y `assets/minecraft/atlases/blocks.json`
>   (sources `directory` namespace `dreamcraft` para `item` y `block`) para
>   garantizar el stitch de la textura `dreamcraft:item/nucleus/icon` en el
>   atlas de bloques.
> - Botones 2×2 por cuadrantes (hotfix pack 4): 8 grupos × 4 cuadrantes 16×16
>   (`textures/item/menu/q/` + modelos `models/item/menu/q/`), declarados en
>   `paper.json` thresholds 41501–41532 — ver tabla "Botones 2×2" en §3.
> - Alineación de cuadrantes y fondos (hotfix pack 5):
>   - Los 44 PNG de `textures/item/menu/q/` estaban en 18×18 (overflow de 2 px
>     por lado en cada slot); recortados centrados a 16×16. Con el
>     `scale: 1.125` de los modelos el tamaño efectivo vuelve a ser 18 px y el
>     botón 2×2 encaja exacto en sus 4 slots.
>   - Los 18 fondos `bg_*` regenerados a las **alturas documentadas** de §6
>     (61/79/97/115/133/151; antes 56..146) por 9-slice desde el marco de la
>     hoja (bbox x750..894 y694..855, slice 24 px) con rectángulo navy opaco
>     detrás; `gui.json` corregido con `height` = alto nativo del PNG (antes
>     52..142, glifo escalado y borroso). Temas: steel = saturación ×0.55 del
>     marco base, matriz = marco base, nexo = hue +40°. Script:
>     `resource-packs/scripts/regen_menu_backgrounds.py`.
>   - Arte nuevo: `city.admin` (grupo de personas, hoja 06), `estate.instance`
>     (portal, hoja 07) y `ward.orphan` (placeholder barrera magenta 32×32).
>     Dispatchers `command_block.json` (41204, fallback `block/command_block`),
>     `end_portal_frame.json` (41303, fallback `block/end_portal_frame`) y
>     `barrier.json` (41106, fallback `item/barrier`). Script:
>     `resource-packs/scripts/gen_missing_icons.py`.
>   - `menu.deposit` re-crop desde la hoja `Dreamcraft-menu-sprites.png`
>     (gema azul, x1370..1396 y195..223): antes duplicaba el "+" azul de
>     `menu.invite`.
> - Botones 2×2 y fondos corregidos en cliente (hotfix pack 6):
>   - Los 44 cuadrantes se regeneran **dividiendo los iconos base 32×32
>     transparentes** de la tabla "Botones 2×2" (`split_quadrants.py`): los
>     anteriores salían de los botones de la hoja con fondo navy horneado y
>     alpha 255, y el solape de 1px entre slots pintaba una cruz negra en
>     medio de cada botón 2×2.
>   - Alturas de los 18 fondos corregidas a 18·n+29 (47/65/83/101/119/137):
>     las documentadas (61..151) dejaban ~11 px de borde vanilla visible bajo
>     el marco (el glifo terminaba en y≈68 con la caja superior en y≈71).
>     `gui.json` actualizado en consecuencia.
>   - El 9-slice ahora re-escala (en vez de recortar) la banda central
>     vertical del marco, conservando el glow del interior (antes quedaba
>     plano y oscuro).

## 4. Sonidos

Los valores actuales son claves **vanilla** (`ui.button.click`,
`block.note_block.bass`, `entity.experience_orb.pickup`) — no requieren
archivos. Para sonidos propios:

```text
assets/dreamcraft/sounds/menu/click.ogg          + entrada en sounds.json
clave resultante: dreamcraft:menu.click          → actualizarla en presentation-assets.yml
```

## 5. Fuentes y símbolos

| Clave | Archivo requerido |
|---|---|
| fuente `dc.hud` | `assets/dreamcraft/font/hud.json` + `assets/dreamcraft/textures/font/hud.png` |
| símbolo `bar.full` | glifo `\uE000` dentro del atlas anterior |
| símbolo `bar.empty` | glifo `\uE001` |
| fuente `dc.gui` | `assets/dreamcraft/font/gui.json` + `textures/font/gui/bg_9..54.png` (ver seccion 6) |
| fuente gotica decorativa (texto futuro) | `assets/dreamcraft/font/gothic.json` + `assets/dreamcraft/font/gothic.ttf` - licencia OFL en `licenses/OldEnglishGothicPixel/` |

## 6. Fondo de menús (glifos GUI)

El fondo de los menús se dibuja con **glifos de fuente** (títulos de
inventario), no con ítems. La fuente es `dreamcraft:gui`, declarada en
`assets/dreamcraft/font/gui.json`.

| Codepoint | Archivo | Tamaño del PNG |
|---|---|---|
| `\uE100` | `textures/font/gui/bg_9.png` | 176×47 |
| `\uE101` | `textures/font/gui/bg_18.png` | 176×65 |
| `\uE102` | `textures/font/gui/bg_27.png` | 176×83 |
| `\uE103` | `textures/font/gui/bg_36.png` | 176×101 |
| `\uE104` | `textures/font/gui/bg_45.png` | 176×119 |
| `\uE105` | `textures/font/gui/bg_54.png` | 176×137 |

- Un glifo por tamaño de inventario (n = filas de 9 slots): alto = 18·n + 29.
  Con `"ascent": 24` y baseline 13 (título vanilla en y=6), el glifo arranca en
  y = −11 (tapa el borde superior vanilla) y su borde inferior cae exactamente
  en y = 18·n + 18 (fin de la caja superior del GUI), sin invadir el label
  "Inventory" (y = 20 + 18·n). Lo que sobra por arriba se clipea sin efecto.
- Todos los bitmaps declaran `"ascent": 24` (overflow superior: el glifo sobrepasa 12 px por arriba del GUI, tapando las esquinas grises del borde vanilla) y `"height"` con el alto nativo
  del PNG (sin `height` Minecraft escala el glifo a 8 px y el fondo no se ve).
- Espacios propios (provider `space`) el compositor del plugin antepone 2x `\uEC04` para anclar el fondo en x=0; el resto, ajuste fino:
  `\uEC01` −1 · `\uEC02` −2 · `\uEC04` −4 · `\uEC05` −8 · `\uEC06` −16 · `\uEC07` −32 · `\uEC08` −64 · `\uEC09` −128 ·
  `\uEC11` +1 · `\uEC12` +2 · `\uEC13` +4 · `\uEC14` +8 · `\uEC15` +16 · `\uEC16` +32 · `\uEC17` +64 · `\uEC18` +128.

> Composición: cada PNG de fondo es un **panel marco** (borde ornato azul/violeta
> + interior navy opaco) construido por 9-slice desde la hoja
> `Dreamcraft-menu-sprites.png`; cubre el GUI completo sin depender del grid
> de slots (el borde queda detrás de los slots y el interior detrás del
> inventario).

## 7. Partículas

Vanilla (`ENCHANT`) — no requieren archivos.

---

## 8. Checklist de entrega del pack

- [ ] PNGs de iconos en sus rutas exactas (§3)
- [ ] Modelos JSON por cada icono referenciando su textura
- [ ] Declaraciones CMD↔modelo incluidas en el pipeline elegido
- [ ] 6 PNGs de fondo de menú en `textures/font/gui/` (§6)
- [ ] (Opcional) sonidos/fuentes según §4–5
- [ ] Probar con `menus.provider: auto`: con pack → iconos custom; sin pack → fallbacks vanilla
