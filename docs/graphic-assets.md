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
3. PNG, minúsculas, sin espacios; resolución recomendada **32×32** (mín. 16×16).
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
| `ward.icon` | SHIELD | 41101 | `textures/item/ward/icon.png` |
| `ward.inactive` | CRACKED_STONE_BRICKS | 41102 | `textures/item/ward/inactive.png` |
| `ward.tier` | NETHER_STAR | 41103 | `textures/item/ward/tier.png` |
| `ward.score` | EXPERIENCE_BOTTLE | 41104 | `textures/item/ward/score.png` |
| `ward.upkeep` | CHEST | 41105 | `textures/item/ward/upkeep.png` |
| `city.icon` | BEACON | 41201 | `textures/item/city/icon.png` |
| `city.members` | PLAYER_HEAD | — | *(vanilla, sin archivo)* |
| `city.treasury` | GOLD_BLOCK | 41202 | `textures/item/city/treasury.png` |
| `city.score` | EMERALD | 41203 | `textures/item/city/score.png` |
| `estate.icon` | BOOK | 41301 | `textures/item/estate/icon.png` |
| `estate.adventure` | ENDER_EYE | 41302 | `textures/item/estate/adventure.png` |
| `estate.instance` | END_PORTAL_FRAME | 41303 | `textures/item/estate/instance.png` |
| `estate.dragon` | DRAGON_HEAD | 41304 | `textures/item/estate/dragon.png` |
| `menu.deposit` | LIME_STAINED_GLASS_PANE | 41401 | `textures/item/menu/deposit.png` |
| `menu.filler` | GRAY_STAINED_GLASS_PANE | — | *(vanilla)* |
| `menu.back` | ARROW | 41402 | `textures/item/menu/back.png` |

> Nota: los ítems con identidad especial (SHIELD, PLAYER_HEAD) pueden exigir
> manejo particular en el pipeline del pack; si complica, pedir remapear su
> `material` en `presentation-assets.yml` antes de dibujar.

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

## 6. Partículas

Vanilla (`ENCHANT`) — no requieren archivos.

---

## 7. Checklist de entrega del pack

- [ ] 14 PNGs de iconos en sus rutas exactas (§3)
- [ ] Modelos JSON por cada icono referenciando su textura
- [ ] Declaraciones CMD↔modelo incluidas en el pipeline elegido
- [ ] (Opcional) sonidos/fuentes según §4–5
- [ ] Probar con `menus.provider: auto`: con pack → iconos custom; sin pack → fallbacks vanilla
