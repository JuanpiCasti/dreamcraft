# Artefactos y configuración de referencia - DreamCraft Protection & Resource Pack v0.1.2 (Fix)

Copia de los archivos oficiales para instalar o desplegar el sistema completo de **DreamCraft Protection** y su **Resource Pack** correspondiente a la versión **0.1.2 (con Hotfix de Contenedores y Upkeep)**.

---

## Contenido de la Release v0.1.2 Fix

| Archivo | Origen | Qué es |
|---|---|---|
| `dreamcraft-protection-0.1.2.jar` | `build/libs/` | Plugin compilado para Paper 1.21.x con las correcciones de acceso a cofres y activación por upkeep. |
| `dreamcraft-resource-pack-0.1.2-dc83d6cb.zip` | `resource-packs/dist/` | Resource Pack oficial listo para Minecraft 1.21.4+ (hash SHA1: `dc83d6cb8c24b47da2241e8e857fbc9670b8dce0`). Incluye fondos de UI horneados HD, texturas de menús, botones 1x1 y bloques 2x2 / 3x2 / 3x3. |
| `config.yml` | `plugin-configs/DreamCraftProtection/config.yml` | Configuración del plugin (modos de presentación `auto`/`rp`/`vanilla`, recipe del núcleo, opciones de Upkeep y aventura). |
| `presentation-assets.yml` | `plugin-configs/DreamCraftProtection/presentation-assets.yml` | Contrato de assets gráficos entre el plugin y el Resource Pack: mapa de CustomModelData (CMDs), glifos de fuentes (`dc.gui`), y sonidos. |
| `messages.yml` | `plugin-configs/DreamCraftProtection/messages.yml` | Overrides de mensajes localizados (vocabulario y lore de El Despertar). |
| `plugin.yml` | `build/resources/main/plugin.yml` | Manifiesto de Paper con la versión `0.1.2`, comandos (`/protection`, `/sync`, `/matriz`, `/nexo`) y permisos. |
| `dreamcraft-protection-system-0.1.2.zip` | Automático | Archivo combinado que contiene todos los elementos anteriores para un despliegue rápido. |
| `dreamcraft-protection-system-0.1.2-fix.zip` | Automático | Copia idéntica con el tag `-fix` para conveniencia de distribución. |

---

## Novedades y Correcciones en la v0.1.2 Fix

1. **Corrección de Acceso a Cofres para el Dueño (`chest-access`)**:
   - En WorldGuard, el flag `chest-access: DENY` ahora se aplica estrictamente al subgrupo `RegionGroup.NON_MEMBERS`.
   - **Resultado**: Al desactivar los contenedores públicos (`PUBLIC_CONTAINERS: false`), el creador del Núcleo y los miembros de la ciudad conservan acceso total a sus propios cofres e inventarios, bloqueando únicamente a terceros.
2. **Activación de Protección Condicionada al Upkeep**:
   - Al colocar el bloque de Núcleo sin mantenimiento (`upkeepBalance <= 0`), la región inicia en modo suspendido (`PASSTHROUGH: ALLOW`), permitiendo el paso y construcción como naturaleza salvaje.
   - En el instante en que el jugador deposita mantenimiento (vía menú de bóveda, comando `/sync alimentar` o interfaz de Núcleo), la protección se activa inmediatamente en WorldGuard.
   - Si el mantenimiento se agota en el cobro periódico, la protección se suspende automáticamente hasta nuevo depósito.
3. **Sistema de Miembros de Protección Directos en Sync sin Matriz (Slot 42)**:
   - Añadido botón 1x1 en la fila 5 (Slot 42) del menú `/sync` con el icono azul (`invite_sync` / `crop_00_10_103_1190x340.png`) en ambos Resource Packs (Java y Bedrock).
   - Permite invitar amigos y organizar parcelas protegidas directamente sin necesidad de crear o pertenecer a una Matriz (Ciudad).
   - Los miembros invitados pueden construir, romper y abrir contenedores dentro del territorio del Sync.
   - El creador que colocó el bloque permanece como dueño absoluto: solo él puede cambiar permisos, invitar/expulsar o apagar el Núcleo.
   - Comandos complementarios: `/sync invitar <jugador>` y `/sync expulsar <jugador>` (alias: `/sync invite`, `/sync kick`).
4. **Valores de Upkeep y Bóveda para Bloques de Ores y Manzanas Doradas**:
   - Ampliado el catálogo de `upkeep-materials` para admitir bloques minerales completos (equivalentes a 9x lingotes) y manzanas doradas:
     - `NETHERITE_BLOCK`: 1024 u
     - `DIAMOND_BLOCK`: 576 u
     - `EMERALD_BLOCK`: 432 u
     - `ENCHANTED_GOLDEN_APPLE`: 512 u
     - `GOLDEN_APPLE`: 128 u
     - `GOLD_BLOCK`: 144 u
     - `IRON_BLOCK`: 72 u
     - `COPPER_BLOCK`: 36 u
     - `LAPIS_BLOCK`: 36 u
     - `REDSTONE_BLOCK`: 36 u
     - `COAL_BLOCK`: 18 u
   - Estos valores son computados automáticamente tanto para el mantenimiento del Sync como para el valor del tesoro de la Matriz.
5. **Aislamiento Nativo Java vs Bedrock**:
   - Los jugadores de Bedrock reciben títulos de menú limpios (sin caracteres `???`) e iconos individuales adaptados.
   - Los jugadores de Java conservan sus fondos gráficos HD horneados en el título sin alteraciones.

---

## Instrucciones de Instalación en Servidor Remoto

1. **Subir el Plugin**:
   - Colocar `dreamcraft-protection-0.1.2.jar` en la carpeta `plugins/` del servidor. Eliminar cualquier JAR anterior.
2. **Subir las Configuraciones**:
   - Colocar `config.yml`, `messages.yml` y `presentation-assets.yml` en la carpeta `plugins/DreamCraftProtection/`.
3. **Configurar el Resource Pack**:
   - Subir el resource pack a su servidor web o CDN y configurar `server.properties` o el servicio de packs con el hash correspondiente.
4. **Reiniciar**:
   - Reiniciar el servidor por completo para cargar el nuevo jar y sincronizar las regiones existentes de WorldGuard.
