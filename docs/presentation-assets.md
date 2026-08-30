# presentation-assets · Contrato plugin ↔ Resource Pack

> **Audiencia:** equipo del worktree `resource-packs` y admins de servidor.
> Este documento define la única interfaz entre el código del plugin
> `DreamCraftProtection` y el Resource Pack visual de DreamCraft.

---

## 1. Principio

El plugin **no conoce** texturas, modelos ni proveedores concretos. Conoce
**claves semánticas** (`ward.icon`, `city.score`, …) y las resuelve contra un
archivo de contrato:

```text
plugin (claves semánticas)  ⇄  presentation-assets.yml  ⇄  resource pack (CMDs/texturas)
```

Reglas de oro (arquitectura §9/§23):

1. Toda entrada es opcional — lo ausente cae a vanilla sin romper nada.
2. El pack nunca decide lógica: solo apariencia.
3. El plugin compila y funciona sin Oraxen, sin DeluxeMenus y sin pack.

---

## 2. Ubicación y precedencia

| Nivel | Ruta | Quién lo edita |
|---|---|---|
| 1 (gana) | `<servidor>/plugins/DreamCraftProtection/presentation-assets.yml` | admin del servidor |
| 2 | embebido en el JAR (`src/main/resources/presentation-assets.yml`) | equipo plugin |

En producción, nivel 1 se copia desde
`plugin-configs/DreamCraftProtection/presentation-assets.yml` vía `config-sync`.

---

## 3. Formato

```yaml
options:
  enabled: true          # interruptor maestro; false = todo vanilla

icons:
  <clave>:               # p.ej. ward.icon — ojo: Bukkit anida por puntos,
    material: SHIELD     #   "ward.icon" equivale a icons.ward.icon (soportado)
    cmd: 41101           # CustomModelData aplicado SOLO si el jugador cargó el pack
    fallback: BEACON     # material para jugadores SIN pack (opcional)
    hide-name: true      # oculta el nombre vanilla mientras renderiza el CMD (opcional)

sounds:
  menu.click: ui.button.click            # forma corta (key vanilla)
  menu.error: { vanilla: block.note_block.bass }

fonts:
  dc.hud: "dreamcraft:hud"               # claves Adventure namespace:key
  dc.gui: "dreamcraft:gui"               # fuente de la GUI (fondos y offsets)

symbols:
  bar.full: "\uE000"                     # glifo suelto…
  bar.full: { glyph: "\uE000", font: dc.hud }  # …o con fuente asociada
  menu.bg.54: { glyph: "\uE105", font: dc.gui }

particles:
  ward.active: ENCHANT                   # nombre del enum Bukkit Particle
```

### Resolución por icono (§9)

```text
viewer cargó el pack Y cmd > 0  → material + CMD (y nombre oculto si hide-name)
si no                           → fallback (o material base) sin CMD
clave ausente                   → mapa legacy del provider → PAPER
```

---

## 4. Claves semánticas vigentes (v1)

| Clave | Dominio | Uso actual |
|---|---|---|
| `ward.icon`, `ward.inactive`, `ward.tier`, `ward.score`, `ward.upkeep` | Ward | menú del Ward |
| `city.icon`, `city.members`, `city.treasury`, `city.score` | City | menú de ciudad |
| `estate.icon`, `estate.adventure`, `estate.instance`, `estate.dragon` | Estate | lobby/instancia |
| `menu.deposit`, `menu.filler`, `menu.back` | genérico | slots comunes |
| `menu.profile` (cmd 41403), `menu.close` (cmd 41404) | genérico | botones Perfil/Cerrar — con pack renderizan PAPER+CMD con nombre oculto (`hide-name`); sin pack, PLAYER_HEAD/BARRIER visibles |
| `menu.invite` (cmd 41405), `menu.kick` (cmd 41406), `menu.roles` (cmd 41407), `menu.members` (cmd 41408), `menu.confirm` (cmd 41409) | genérico | gestión de menús/miembros: Invitar, Expulsar, Roles, Miembros, Confirmar — con pack renderizan PAPER+CMD con nombre oculto (`hide-name`); sin pack, PAPER. Consumidos por City/Estate/Ward menu builders |
| `estate.zone-tp` (cmd 41305) | Estate | botón TP al centro del núcleo |
| Cuadrantes 2×2: `icon.upkeep.*` (41501-04), `icon.ward.tier.*` (41505-08), `icon.city.overview.*` (41509-12), `city.treasury.*` (41513-16), `menu.invite.*` (41517-20), `menu.roles.*` (41521-24), `icon.estate.overview.*` (41525-28), `icon.back.*` (41529-32), `icon.ward.active.*` (41533-36), `icon.ward.inactive.*` (41537-40), `icon.estate.zone-tp.*` (41541-44) | genérico | botones extendidos: cada bloque 2×2 pinta un solo botón grande con 4 tiles tl/tr/bl/br (PAPER+CMD); los 4 slots disparan la misma acción. Menús de jugador en 54 slots; admin overviews paginan 8 entradas/página |
| `nucleus.icon` (cmd 41002) | Nucleus | ítem físico del núcleo: LODESTONE con CMD (`items/lodestone.json` del pack, override de `block/lodestone` para colocado); beacon vanilla intacto |
| `sounds.menu.click / menu.error / menu.success` | genérico | feedback de acciones |
| `fonts.dc.gui` | genérico | fuente de la GUI del resource pack |
| `symbols.menu.bg.*`, `symbols.menu.offset.*` | genérico | fondo gráfico y offsets de la GUI (ver «Títulos adaptativos») |
| `fonts.*`, `particles.*` | genérico | reservado (API ya disponible en `PresentationAssetRegistry`) |

Los iconos se consumen vía `MenuItem.iconKey()` → `VanillaMenuProvider.applyAssets()`.
Nuevas claves solo requieren añadir la entrada al YAML — cero código.

### Títulos adaptativos

Con el pack cargado, el título del inventario no es texto: es **un glifo HD
de fondo** que cubre todo el contenedor vanilla (176 px de ancho, ascent 13,
diseñado para tapar la cuadrícula). Hay un codepoint por tamaño de inventario:

```text
menu.bg.9   → \uE100      menu.bg.36 → \uE103
menu.bg.18  → \uE101      menu.bg.45 → \uE104
menu.bg.27  → \uE102      menu.bg.54 → \uE105
```

`MenuTitleComposer.compose()` (llamado por `VanillaMenuProvider.open()`)
resuelve `menu.bg.<size>` y lo emite como título con fuente `dc.gui`; los
slots vacíos se dejan en AIR para que el fondo se vea a través. Sin pack (o
con `menus.custom-title: false`) el título vuelve al texto legacy y los slots
recuperan el filler de vidrio.

Anclaje: la etiqueta del contenedor se dibuja en `x=8`, así que el compositor
antepone dos espacios propios `menu.offset.-4` (`\uEC04`, advance −4) para
llevar el glifo de 176 px a `x=0`. Si el contrato no define el offset, el
glifo se emite solo (queda 8 px desplazado, sin romper).

Los fondos miden `176 × (112 + filas×18)` px: cubren contenedor **y** el
inventario del jugador (130 px con 1 fila … 220 px con 6).

---

## 5. Flujo de trabajo para el equipo `resource-packs`

1. Elegir los rangos CMD definitivos y **editar este archivo** (nivel 1 o el
   embebido si el cambio es global).
2. Desarrollar las texturas/modelos apuntando a esos CMDs.
3. Probar con `menus.provider: auto`: con pack → CMD; sin pack → `fallback`.
4. Nunca renombrar una clave semántica: es API pública entre directorios.
   Añadir claves nuevas es libre.

## 6. Extensión futura (fuera de alcance v1)

- `OraxenResourcePackAdapter implements ResourcePackProvider` — cuando Oraxen
  entre al stack, resuelve item-ids en lugar de CMD; el puerto
  (`presentation/resourcepack/ResourcePackProvider.java`) ya está definido.
- `DeluxeMenusAdapter` como segundo `MenuProvider`.
- Sonidos custom, glifos en mensajes (`Messages`) y partículas por estado.
- Pantalla de perfil del jugador (el botón Perfil hoy es un placeholder con
  feedback de marca).
