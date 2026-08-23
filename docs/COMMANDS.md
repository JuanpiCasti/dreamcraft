# Comandos del plugin — DreamCraftProtection

Referencia de todos los comandos que registra el plugin (`src/main/resources/plugin.yml`).
Los comandos son solo para jugadores; excepciones ejecutables desde consola/RCON
(ops admin sin ubicación): `/protection reload`, `/protection integrations`,
`/estate disband <id>`, `/estate admin reset <id>`, `/ward admin delete <uuid|nombre>`
y `/city admin delete <nombre>`.

| Comando | Aliases | Permiso de uso |
|---|---|---|
| `/protection` | `/prot`, `/claim` | `dreamcraft.protection.use` |
| `/ward` | `/w` | `dreamcraft.ward.use` |
| `/city` | `/ciudad` | `dreamcraft.city.use` |
| `/estate` | `/grupo` | `dreamcraft.estate.use` |

Los cuatro permisos de uso tienen `default: true` (cualquier jugador los tiene).

---

## `/protection` — Claims de base

Gestiona el claim clásico por armario (Wardrobe). Los subcomandos operan sobre
el claim en el que estás parado.

### Jugador

| Comando | Descripción |
|---|---|
| `/protection` | Muestra la ayuda |
| `/protection claim` | Abre el menú del claim actual (VIPs y gobernadores: ver "Acceso al menú") |
| `/protection status` | Estado del claim: nombre, owner, tier, radio, miembros |
| `/protection upkeep` | Unidades de mantenimiento almacenadas y próximo cobro |
| `/protection members` | Ayuda de gestión de miembros |
| `/protection members list` | Lista miembros y slots usados/totales |
| `/protection members add <jugador>` | Agrega un miembro (solo owner, respeta el límite del tier) |
| `/protection members remove <jugador>` | Quita un miembro (acepta nombre o UUID; solo owner) |
| `/protection permissions` | Lista los permisos públicos del claim |
| `/protection permissions <permiso> grant` | Concede un permiso público (solo owner) |
| `/protection permissions <permiso> revoke` | Revoca un permiso público (solo owner) |
| `/protection upgrade [tier]` | Mejora el claim al siguiente tier o a uno específico (solo owner) |
| `/protection transfer <jugador>` | Transfiere el ownership (se puede deshabilitar por config) |
| `/protection dissolve` | Elimina el claim y levanta la protección (alias: `abandon`; solo owner) |

**Permisos públicos válidos para claims**: `PUBLIC_BUILD`, `PUBLIC_BREAK`,
`PUBLIC_CONTAINERS`. Se guardan en la configuración del claim. `PUBLIC_CONTAINERS`
abre los contenedores (cofres, barriles, hornos) a no-miembros — espeja el flag
`chest-access` de la región WG, que por defecto está en `deny` para outsiders
(owner y miembros siempre tienen acceso). Los ítems además no pueden cruzar el
borde del claim vía hoppers/droppers (`InventoryMoveItemEvent`).

### Acceso al menú

- **Principal**: clic derecho sobre el armario (Wardrobe) — disponible para
  cualquier jugador sobre su propio claim.
- **`/protection claim`**: requiere `dreamcraft.protection.menu`
  (default: false, pensado para VIPs) o ser Gobernador de una ciudad.

### Admin (`dreamcraft.protection.admin`, default: op)

| Comando | Descripción |
|---|---|
| `/protection give` | Entrega el ítem Wardrobe |
| `/protection reload` | Recarga la configuración del plugin |
| `/protection recalculate` | Recalcula el estado de upkeep del claim actual (alias: `rebuildstats`) |

---

## `/ward` — Wards

Territorio personal con tiers por score, upkeep y anexión a ciudades.
Si no indicás un `[id]`, el Ward se resuelve así: ID en los args → Ward en tu
posición → primer Ward que te pertenece.

### Creación con la Baliza de Ward

1. Reclamás tu primer núcleo gratis con `/ward reclamar` (una vez por jugador,
   persistido), lo crafteás con la receta de `ward.recipe` (por defecto
   8 diamantes + estrella del Nether) o un admin te lo entrega (`/ward give`).
2. Colocás la Baliza: se funda el Ward centrado en ese bloque (falla si el
   lugar queda dentro de otro claim o Ward).
3. Clic derecho sobre la Baliza abre el menú del Ward.
4. Romper la Baliza disuelve el Ward a través del contrato único de disolución
   (solo el owner o un admin pueden): el drop vainilla se suprime y el owner
   recibe de vuelta el ítem taggeado (al inventario, o a sus pies si está
   lleno). Un admin que disuelve el Ward ajeno no recibe nada.

El bloque, el item-id, el custom model data, el score por mejora, la receta de
crafteo y los ítems aceptados se configuran en `config.yml` (sección `ward:`).

| Comando | Descripción |
|---|---|
| `/ward` | Muestra la ayuda |
| `/ward create` | Crea un Ward centrado en tu posición (crea también la región de WorldGuard) |
| `/ward reclamar` | Entrega tu primer Núcleo gratis (una vez por jugador; inventario lleno → drop a los pies) |
| `/ward give` | Entrega la Baliza de Ward (admin) |
| `/ward info [id]` | Nombre, ID, owner, tier, score, radio, upkeep y centro |
| `/ward menu [id]` | Abre el menú gráfico del Ward (requiere `dreamcraft.ward.menu` o admin; ver abajo) |
| `/ward score [add <n>]` | Consulta el score; con `add` suma puntos (**solo** `dreamcraft.ward.admin`: el owner ya no puede subir fases por comando) y redimensiona la región — rechaza crecimientos que alcanzarían una Ward ajena |
| `/ward upkeep [deposit <n>]` | Consulta el upkeep; con `deposit` deposita unidades |
| `/ward upgrade` | Mejora el Ward al siguiente tier descontando los ítems definidos en `ward-upgrade-costs` (solo owner). Falla si el radio nuevo alcanzaría la Ward de otro jugador |
| `/ward transfer <jugador>` | Transfiere el ownership a un jugador online (solo owner) |
| `/ward permissions` | Lista los permisos públicos actuales |
| `/ward permissions <permiso> grant` | Concede un permiso público (solo owner) |
| `/ward permissions <permiso> revoke` | Revoca un permiso público (solo owner) |
| `/ward city` | Muestra la ciudad a la que pertenece el Ward |
| `/ward city annex` | Anexa el Ward a tu ciudad (solo owner, debés ser miembro de una ciudad) |
| `/ward city leave` | Desvincula el Ward de su ciudad (solo owner) |
| `/ward delete [id]` | Elimina el Ward y su región vía el contrato único de disolución (solo owner o admin; owner recibe su Núcleo de vuelta) |
| `/ward admin menu` | Panel admin de Núcleos (lore del servidor: `/sync admin menu`); sin argumentos |
| `/ward admin delete <uuid\|nombre>` | Disolución forzada por UUID o nombre EXACTO del Núcleo (ruta sistema: el owner no recibe nada); acepta consola/RCON (lore: `/sync admin delete`) |

`admin menu` abre una GUI sin estado paginada por payloads (`wardadmin.*` en
`MenuActionDispatcher`): overview de 54 slots (45 ítems/página) con los
huérfanos primero y luego orden alfabético; «Anterior» en 45, toggle «Solo
sospechosos» en 49 (huérfanos o estado desconocido: chunk sin cargar / WG
inactivo) y «Siguiente» en 53. El detalle de cada Núcleo (27 slots) ofrece
*Abrir menú del Núcleo*, *TP al centro* y *DISOLVER NÚCLEO*.

**Permisos públicos válidos** (`WardPermission`): `PUBLIC_BUILD`, `PUBLIC_BREAK`,
`PUBLIC_CONTAINERS`, `PUBLIC_UPKEEP_DEPOSIT`, `PUBLIC_STATUS_VIEW`.
`PUBLIC_CONTAINERS` alterna el flag WG `chest-access` de la región (deny por
defecto al fundar el Ward; grant = allow). Los hoppers/droppers externos no
pueden drenar contenedores dentro del Ward (ni empujar hacia afuera), salvo
entre Wards del mismo dueño o de la misma ciudad.

Admin: `dreamcraft.ward.admin` (default: op) permite borrar/modificar Wards ajenos.
Menú: `dreamcraft.ward.menu` (default: false, pensado para VIPs) habilita
`/ward menu`; los admins siempre pueden. El acceso principal al menú sigue
siendo el clic derecho sobre la Baliza.

### Bloques gated y sobrecosto

Colocar un bloque de `ward.tier-gated-blocks` dentro de un Ward cuyo tier es
inferior al requerido **no se cancela**: se permite y suma 1 al contador
`belowTierBlocks` del Ward (ítem fundador exento; admins bypass). El upkeep de
cada intervalo pasa a costar `upkeep-per-interval` del tier +
`ward.below-tier-surcharge-units` (default 2) × bloques fuera de fase — cargo
**recurrente por intervalo**, sin cobro único al colocar. Romper uno de esos
bloques mientras el Ward siga debajo del tier decrementa el contador (no se
registra quién lo colocó). Al alcanzar el tier requerido los bloques dejan de
sumar sobrecosto.

El contador se siembra al fundar: tanto `/ward create` como colocar la Baliza
corren un re-scan inicial que cuenta los bloques gated pre-existentes dentro
del radio y fija el contador con ese valor (los chunks no cargados quedan
exentos hasta el próximo re-scan). Toda transición de tier pasa por el único
punto de paso `WardService.addBaseScore`: subir de tier limpia el contador a 0
(«Fase alineada…» avisa al owner — la fase nueva ya cubre esos bloques) y bajar
de tier dispara un re-scan que REEMPLAZA el contador por el conteo actual del
área; dentro del mismo tier no se toca.

### Curva de mejoras (esquema B)

Cada mejora suma `ward.score-per-upgrade` al base score; tier y radio se
recalculan desde el score nuevo, así que **el radio crece en cada mejora**,
pague o no. Solo cobra ítems la mejora que **cruza** el `min-base-score` del
tier destino (`ward-upgrade-costs`, clave = tier destino); las mejoras
intermedias son gratis («crecimiento»). Con los defaults (paso 100,
reinforced ≥ 100, advanced ≥ 500): la mejora #1 cruza a *reinforced* y paga;
#2–#4 son gratis; la que alcanza score 500 cruza a *advanced* y paga.

---

## `/city` — Ciudades

Gobernador, consejo, tesoro y políticas. Si no indicás `[nombre]`, la ciudad se
resuelve por tu membresía.

| Comando | Descripción |
|---|---|
| `/city` | Muestra la ayuda |
| `/city create <nombre>` | Crea una ciudad; quedás como Gobernador |
| `/city info [nombre]` | Gobernador, miembros, tesoro, score, wards y políticas |
| `/city menu` | Abre el menú gráfico de la ciudad |
| `/city annex <wardId>` | Anexa un Ward a tu ciudad (miembro o gobernador) |
| `/city invite <jugador>` | Invita un residente (Gobernador o Council) |
| `/city kick <jugador>` | Expulsa un residente (Gobernador o Council; no se puede expulsar al Gobernador) |
| `/city roles <jugador> <rol>` | Asigna rol (solo Gobernador). Roles: `GOVERNOR`, `COUNCIL`, `CITIZEN`, `ALLY`. Asignar `GOVERNOR` transfiere la gobernanza |
| `/city bank deposit <monto>` | Deposita en el tesoro (Gobernador o Council) |
| `/city bank withdraw <monto>` | Retira del tesoro si hay fondos (Gobernador o Council) |
| `/city policy set <politica> <on\|off>` | Cambia una política (solo Gobernador) |
| `/city transfer <jugador>` | Transfiere la gobernanza a un miembro (solo Gobernador) |
| `/city delete` | Elimina la ciudad y desanexa sus Wards (solo Gobernador o admin) |

**Políticas válidas** (`CityPolicy`): `AUTO_ASSOCIATE_WARDS`, `OPEN_RECRUITMENT`,
`FREE_WARD_CREATION`, `COUNCIL_TREASURY_APPROVAL`, `PUBLIC_LISTING`.

Admin: `dreamcraft.city.admin` (default: op).

| Comando | Descripción |
|---|---|
| `/city admin menu` | Panel admin de ciudades (lore del servidor: `/matriz admin menu`); sin argumentos |
| `/city admin delete <nombre>` | Elimina la ciudad indicada por nombre exacto (acepta nombres con espacios; lore: `/matriz admin delete`); acepta consola/RCON |

Misma GUI paginada que la de Núcleos (payloads `cityadmin.*`), pero orden
alfabético puro — las ciudades no tienen salud, así que no hay filtro de
sospechosos. El detalle de cada Matriz ofrece *Abrir menú de ciudad* y
*ELIMINAR CIUDAD* (desanexa todos sus Núcleos): **sin TP**.

---

## `/estate` — Estates (grupos de aventura)

Grupos temporales para instancias de aventura.

Tipos de aventura: `end` (portal del End con instancia privada) y
`trial_chamber` (cámara de pruebas con vaults reservados). `standard` es un
grupo sin mecánicas de mundo.

| Comando | Descripción |
|---|---|
| `/estate` | Muestra la ayuda |
| `/estate create <id>` | Crea un Estate propio |
| `/estate discover <tipo>` | Genera tu propio Estate de aventura (`end`, `trial_chamber`) con vos como líder y abre su menú |
| `/estate invite <jugador>` | Invita un miembro (solo owner) |
| `/estate join <id>` | Te une a un Estate por ID |
| `/estate leave` | Salís del Estate (el owner no puede salir: debe transferir o disolver) |
| `/estate start` | Inicia la instancia del Estate (solo owner; en estates END pre-crea el mundo con la dragona) |
| `/estate transfer <jugador>` | Transfiere el ownership a un miembro (solo owner) |
| `/estate info` | Tipo, owner, miembros, aventura, área, mundo End y persistencia |
| `/estate menu` | Abre el menú gráfico del Estate |
| `/estate disband` | Disuelve el Estate (solo owner; reinicia su instancia End si tiene) |

### Admin

Requieren `dreamcraft.protection.admin`.

| Comando | Descripción |
|---|---|
| `/estate admin create <id> <tipo> [radio\|auto]` | Crea un Estate administrativo persistente y fija su **área** donde estás parado (default r=32). Con `auto` localiza la estructura vanilla real (stronghold / trial chambers, búsqueda de 512 bloques) y ancla ahí con r=48. Parate dentro de la estructura antes de usar radio manual |
| `/estate admin area <id> [radio]` | Mueve/re-ancla el área del estate a tu posición actual (re-ancla también la banda vertical) |
| `/estate admin reset <id>` | Reinicia la instancia End: borra el mundo privado y deja la dragona lista |

### Sigilo vertical (band-below / band-above)

Las áreas END/TRIAL_CHAMBER **no cubren toda la altura del mundo**:

- La región WG abarca `anclaY - band-below … anclaY + band-above`
  (`estate-instances.band-*` en config.yml; defaults 16/48).
- El descubrimiento de zona usa la misma banda: quien camina por la
  **superficie** sobre una stronghold no recibe mensajes, no dispara la
  creación automática de party y no queda dentro de ninguna región — no
  puede saber que la estructura está abajo.
- Solo a la altura correcta (dentro de la banda) se activan los gates:
  frames/vaults para no-miembros y el aviso de descubrimiento.
- **Regeneración del portal**: al anclar un área (`admin create/area`) se
  captura un snapshot de los frames vanilla. Cada vez que alguien **sale por
  el portal de salida** (y también en el reset programado o `admin reset`),
  la sala del portal se regenera: frames rotos/extraídos vuelven a su sitio,
  los ojos colocados se retiran y los portales abiertos se cierran — la zona
  queda lista para el siguiente grupo.
- **Loot re-armado con re-roll** (parte de `regenerate-zone`): el snapshot
  también registra los contenedores con LootTable vanilla activa. Al cerrar
  el nexo cada cofre se limpia y se vuelve a armar con su MISMA tabla pero
  semilla aleatoria nueva — cada grupo encuentra loot distinto al anterior.
- **Ores y estructura indestructibles** (`estate-instances.protect-structure`,
  default on): dentro del nexo no se puede minar ningún `_ORE` ni
  ANCIENT_DEBRIS, ni romper frames, vaults, trial spawners, spawners o
  contenedores de loot — ningún grupo agota la stronghold para el siguiente.
- **Regeneración de chunks al cerrar** (`regenerate-zone`, default on):
  toda otra modificación (bloques colocados/rotos, cubetas) se journala en
  `zone-edits/<estateId>.log` con su estado original; al salir por el portal
  de salida, en el reset programado o con `admin reset` el journal se aplica
  en orden inverso y los chunks quedan como recién generados. Las explosiones
  no afectan bloques de la zona. Se eligió journaling sobre
  `World.regenerateChunk` porque las columnas pueden llegar hasta bases en
  superficie — el journal solo toca lo que los aventureros modificaron.
- Zonas creadas antes de este cambio quedaron con anclaje Y=0: re-anclarlas
  una vez con `/estate admin area <id>` para posicionar la banda (y capturar
  el snapshot del portal).

### Cómo funciona una aventura de tipo `end`

1. Un admin corre
   `/estate admin create <nombre> end auto` (localiza la stronghold) o se
   paró junto al portal y usa `… end 32`. Eso define la **zona** del área,
   acotada verticalmente por la banda de sigilo.
2. Cada jugador que entra a la zona (o corre `/estate discover end`) obtiene su
   **propio estate de party**: queda como líder, hereda el área de la zona y
   puede invitar a su grupo con `/estate invite <jugador>`.
3. Los frames solo aceptan ojos de miembros de alguna party de la zona; al
   cruzar el portal, cada party viaja a su **mundo End privado**
   (`dc_end_<id>`) con plataforma de obsidiana y dragona fresca — varias
   parties pueden estar peleando en paralelo sin chocarse, y el
   `world_the_end` compartido queda intacto.
4. El primer ingreso de cada party avisa quién ya está "del otro lado" y, tras
   un período de gracia configurable, los ojos del portal de entrada se
   retiran: la próxima party debe volver a armarlo.
5. Cuando el último miembro de una party sale (portal de salida, muerte,
   desconexión), su mundo se descarga y se borra: **el mapa vuelve a su estado
   previo al jefe y la dragona reaparece** para esa party. Las demás parties
   siguen peleando en sus propios mundos.

---

## Tabla completa de permisos

| Permiso | Default | Otorga |
|---|---|---|
| `dreamcraft.protection.use` | todos | Uso de `/protection` |
| `dreamcraft.protection.menu` | nadie | `/protection claim` (VIPs; los gobernadores de ciudad también pueden) |
| `dreamcraft.protection.admin` | op | `give`, `reload`, `recalculate`, `/estate admin` |
| `dreamcraft.ward.use` | todos | Uso de `/ward` |
| `dreamcraft.ward.menu` | nadie | `/ward menu` (VIPs; los admins siempre pueden) |
| `dreamcraft.ward.admin` | op | Borrar/modificar Wards de otros; `abrir`, `admin menu`, `admin delete`, `score add` (exclusivo: el owner ya no puede subir fases) |
| `dreamcraft.city.use` | todos | Uso de `/city` |
| `dreamcraft.city.admin` | op | Administración de ciudades |
| `dreamcraft.estate.use` | todos | Uso de `/estate` |
| `dreamcraft.integrations.status` | op | Ver estado del Integration Registry |

Todos los comandos tienen autocompletado (tab completion) de subcomandos,
jugadores online, roles, políticas y IDs propios.

**Nota operativa (LuckPerms)**: el grupo `admin` necesita los nodos
`dreamcraft.ward.admin` y `dreamcraft.city.admin` otorgados explícitamente —
ambos tienen `default: op`, así que un admin no-op sin ellos no ve los
subcomandos de administración (`/ward|sync admin …`, `/city|matriz admin …`)
ni pasa sus gates.

---

## Nombres automáticos

Claims, Wards, ciudades y Estates generan nombres amigables al crearse
(ej.: "Atalaya del Alba"); si hay colisión se agrega un numeral romano.
El nombre se muestra en menús, `/protection status`, `/ward info` y títulos,
y puede cambiar con el tiempo sin afectar el ID interno.

---

## Personalización por servidor (sin recompilar)

### Renombrar comandos raíz — `commands.yml` de Bukkit

Los comandos raíz (`/ward`, `/city`, `/estate`, `/protection`) se renombran
con el archivo `commands.yml` del servidor, sin tocar el plugin:

```yaml
# server commands.yml
parcela: ward $1-
ciudad:  city $1-
grupo:   estate $1-
```

El alias hereda dispatch, tab-complete de nivel 2+ y permisos. Plantillas
listas en `plugin-configs/DreamCraftProtection/commands.example.yml`.
Limitación: renombra la invocación; el texto de ayuda interno sigue citando
el comando canónico (edítalo en `messages.yml`).

### Aliases y alta/baja de subcomandos — `config.yml`

```yaml
commands:
  ward:
    subcommands:
      create: { aliases: [fundar] }   # /ward fundar ≡ /ward create
      menu:   { enabled: false }      # responde "subcomando desconocido"
```

Los aliases participan del dispatch y del tab-complete de nivel 1. Los
subcomandos admin (`give`, `reload`, `recalculate`, `integrations`,
`estate admin`) se ocultan del tab-complete para quien no tenga el permiso.

### Textos — `messages.yml`

Prefijos (`[Ward]`, `[Ciudad]`…), errores comunes, bloques de ayuda completos
y todo el feedback de menús viven en `messages.yml` (códigos `&` y
placeholders `{nombre}`). Copia el embebido a
`plugin-configs/DreamCraftProtection/messages.yml` para re-marcar textos por
servidor. Resolución: override del servidor → default embebido → fallback en
código.

### Estado de integraciones

`/protection integrations` (permiso `dreamcraft.integrations.status`) muestra
infraestructura (WG, LP, CP…) **y** presentación: modo de assets, proveedor,
iconos en contrato y detección de Oraxen/DeluxeMenus.

---

## Servidor «El Despertar y la Sincronicidad» (lore activo)

Este worktree despliega el lore de marca. Mapeo completo:

| Lore | Implementación | Dónde |
|---|---|---|
| `/sync`, `/sincronia` ≡ Ward | `commands.yml`: `sync: ward $1-` | servidor |
| `despertar`, `renombrar`, `alimentar`, `fase`, `dar`, `apagar`, `nucleo` | aliases por subcomando | `config.yml` → `commands.ward.*` |
| `/nexo` ≡ Estate · `/matriz` ≡ City | `commands.yml` | servidor |
| Presencia física obligatoria para alimentar/mejorar | gate por distancia al centro del Ward (+2 bloques) | `WardCommand.ensurePresence` / `ProtectionCommand.ensurePresence` |
| Enlace dimensional remoto VIP | permiso `dreamcraft.ward.remote` (admins bypass) — alimenta, mejora y `/sync tp` desde cualquier lugar | LuckPerms → grupo VIP |
| `/sync sintonizar` / `/sync expulsar` | concede/revoca flags públicos BUILD+CONTAINERS con sync WG | `WardCommand.handleSintonize/handleExpulsar` |
| `/sync tp` | teletransporte al núcleo; owner o enlace remoto | `WardCommand.handleTp` |
| `/sync abrir <id\|jugador>` | inspección staff: abre el menú de cualquier núcleo | `WardCommand.handleAdminOpen` |

**Nota de diseño**: los Wards no llevan lista de miembros individual
(el acceso se modela vía flags públicos + membresía de Ciudad), así que
`sintonizar/expulsar` operan sobre la frecuencia pública del territorio;
la membresía nominal vive en la Matriz (`/ciudad invitar/expulsar`).

**Pendiente del lore** (requiere dominio): estado de Letargo forzado
(`/sync admin letargo`) y depósito remoto genérico `/sync alimentar [cantidad]`
sin argumentos de material.
