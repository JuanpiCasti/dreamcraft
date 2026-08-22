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

sounds:
  menu.click: ui.button.click            # forma corta (key vanilla)
  menu.error: { vanilla: block.note_block.bass }

fonts:
  dc.hud: "dreamcraft:hud"               # claves Adventure namespace:key

symbols:
  bar.full: "\uE000"                     # glifo suelto…
  bar.full: { glyph: "\uE000", font: dc.hud }  # …o con fuente asociada

particles:
  ward.active: ENCHANT                   # nombre del enum Bukkit Particle
```

### Resolución por icono (§9)

```text
viewer cargó el pack Y cmd > 0  → material + CMD
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
| `sounds.menu.click / menu.error / menu.success` | genérico | feedback de acciones |
| `fonts.*`, `symbols.*`, `particles.*` | genérico | reservado (API ya disponible en `PresentationAssetRegistry`) |

Los iconos se consumen vía `MenuItem.iconKey()` → `VanillaMenuProvider.applyAssets()`.
Nuevas claves solo requieren añadir la entrada al YAML — cero código.

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
