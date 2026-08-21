# Comandos del plugin — DreamCraftProtection

Referencia de todos los comandos que registra el plugin (`src/main/resources/plugin.yml`).
Todos los comandos son solo para jugadores (la consola no puede usarlos).

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
`PUBLIC_INTERACT`. Se guardan en la configuración del claim y permiten que
jugadores ajenos construyan/rompan/interactúen dentro del claim.

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

1. Un admin entrega la Baliza con `/ward give`.
2. Colocás la Baliza: se funda el Ward centrado en ese bloque (falla si el
   lugar queda dentro de otro claim o Ward).
3. Clic derecho sobre la Baliza abre el menú del Ward.
4. Romper la Baliza disuelve el Ward (solo el owner o un admin pueden).

El bloque, el item-id, el custom model data y el score por mejora se configuran
en `config.yml` (sección `ward:`).

| Comando | Descripción |
|---|---|
| `/ward` | Muestra la ayuda |
| `/ward create` | Crea un Ward centrado en tu posición (crea también la región de WorldGuard) |
| `/ward give` | Entrega la Baliza de Ward (admin) |
| `/ward info [id]` | Nombre, ID, owner, tier, score, radio, upkeep y centro |
| `/ward menu [id]` | Abre el menú gráfico del Ward (requiere `dreamcraft.ward.menu` o admin; ver abajo) |
| `/ward score [add <n>]` | Consulta el score; con `add` suma puntos (solo owner) y redimensiona la región |
| `/ward upkeep [deposit <n>]` | Consulta el upkeep; con `deposit` deposita unidades |
| `/ward upgrade` | Mejora el Ward al siguiente tier descontando los ítems definidos en `ward-upgrade-costs` (solo owner) |
| `/ward transfer <jugador>` | Transfiere el ownership a un jugador online (solo owner) |
| `/ward permissions` | Lista los permisos públicos actuales |
| `/ward permissions <permiso> grant` | Concede un permiso público (solo owner) |
| `/ward permissions <permiso> revoke` | Revoca un permiso público (solo owner) |
| `/ward city` | Muestra la ciudad a la que pertenece el Ward |
| `/ward city annex` | Anexa el Ward a tu ciudad (solo owner, debés ser miembro de una ciudad) |
| `/ward city leave` | Desvincula el Ward de su ciudad (solo owner) |
| `/ward delete [id]` | Elimina el Ward y su región (solo owner o admin) |

**Permisos públicos válidos** (`WardPermission`): `PUBLIC_BUILD`, `PUBLIC_BREAK`,
`PUBLIC_INTERACT`, `PUBLIC_UPKEEP_DEPOSIT`, `PUBLIC_STATUS_VIEW`.

Admin: `dreamcraft.ward.admin` (default: op) permite borrar/modificar Wards ajenos.
Menú: `dreamcraft.ward.menu` (default: false, pensado para VIPs) habilita
`/ward menu`; los admins siempre pueden. El acceso principal al menú sigue
siendo el clic derecho sobre la Baliza.

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

---

## `/estate` — Estates (grupos de aventura)

Grupos temporales para instancias de aventura.

| Comando | Descripción |
|---|---|
| `/estate` | Muestra la ayuda |
| `/estate create <id>` | Crea un Estate propio |
| `/estate discover <tipo>` | Descubre un Estate de aventura no persistente y abre su menú |
| `/estate invite <jugador>` | Invita un miembro (solo owner) |
| `/estate join <id>` | Te une a un Estate por ID |
| `/estate leave` | Salís del Estate (el owner no puede salir: debe transferir o disolver) |
| `/estate start` | Inicia la instancia del Estate (solo owner; una activa a la vez) |
| `/estate transfer <jugador>` | Transfiere el ownership a un miembro (solo owner) |
| `/estate info` | Owner, miembros, aventura, instancia y persistencia |
| `/estate menu` | Abre el menú gráfico del Estate |
| `/estate disband` | Disuelve el Estate (solo owner) |

### Admin

| Comando | Descripción |
|---|---|
| `/estate admin create <id> <tipo>` | Crea un Estate administrativo persistente. Requiere `dreamcraft.protection.admin` |

---

## Tabla completa de permisos

| Permiso | Default | Otorga |
|---|---|---|
| `dreamcraft.protection.use` | todos | Uso de `/protection` |
| `dreamcraft.protection.menu` | nadie | `/protection claim` (VIPs; los gobernadores de ciudad también pueden) |
| `dreamcraft.protection.admin` | op | `give`, `reload`, `recalculate`, `/estate admin` |
| `dreamcraft.ward.use` | todos | Uso de `/ward` |
| `dreamcraft.ward.menu` | nadie | `/ward menu` (VIPs; los admins siempre pueden) |
| `dreamcraft.ward.admin` | op | Borrar/modificar Wards de otros |
| `dreamcraft.city.use` | todos | Uso de `/city` |
| `dreamcraft.city.admin` | op | Administración de ciudades |
| `dreamcraft.estate.use` | todos | Uso de `/estate` |
| `dreamcraft.integrations.status` | op | Ver estado del Integration Registry |

Todos los comandos tienen autocompletado (tab completion) de subcomandos,
jugadores online, roles, políticas y IDs propios.

---

## Nombres automáticos

Claims, Wards, ciudades y Estates generan nombres amigables al crearse
(ej.: "Atalaya del Alba"); si hay colisión se agrega un numeral romano.
El nombre se muestra en menús, `/protection status`, `/ward info` y títulos,
y puede cambiar con el tiempo sin afectar el ID interno.
