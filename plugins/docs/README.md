# Artefactos y configuracion de referencia - DreamCraft Protection & Resource Pack v0.1.2

Copia de los archivos oficiales para instalar o desplegar el sistema completo de **DreamCraft Protection** y su **Resource Pack** correspondiente a la version **0.1.2**.

---

## Contenido de la Release v0.1.2

| Archivo | Origen | Que es |
|---|---|---|
| `dreamcraft-protection-0.1.2.jar` | `build/libs/` | Plugin compilado para Paper 1.21.x (release sin SNAPSHOT). Colocar en `plugins/` del servidor. |
| `dreamcraft-resource-pack-0.1.2-31e5e101.zip` | `resource-packs/dist/` | Resource Pack oficial listo para Minecraft 1.21.4+ (hash SHA1: `31e5e1012e25c0eb04eee115bcb970567f5ba7dc`). Incluye fondos de UI horneados HD, texturas de menus, botones 1x1 y bloques 2x2 / 3x2 / 3x3. |
| `config.yml` | `plugin-configs/DreamCraftProtection/config.yml` | Configuracion del plugin (modos de presentacion `auto`/`rp`/`vanilla`, recipe del nucleo, opciones de Upkeep y aventura). |
| `presentation-assets.yml` | `plugin-configs/DreamCraftProtection/presentation-assets.yml` | Contrato de assets graficos entre el plugin y el Resource Pack: mapa de CustomModelData (CMDs), glifos de fuentes (`dc.gui`), y sonidos. |
| `messages.yml` | `plugin-configs/DreamCraftProtection/messages.yml` | Overrides de mensajes localizados (vocabulario y lore de El Despertar). |
| `plugin.yml` | `build/resources/main/plugin.yml` | Manifiesto de Paper con la version expandida a `0.1.2`, comandos (`/protection`, `/sync`, `/matriz`, `/nexo`) y permisos. |
| `dreamcraft-protection-system-0.1.2.zip` | Automatico | Archivo combinado que contiene todos los elementos anteriores para un despliegue rapido. |

---

## Novedades y Cambios en la v0.1.2

1. **Botones de Menu en Fila 5 (Slots 36..44)**:
   - Todos los botones de accion 1x1 en `/sync`, `/matriz` y `/nexo` se encuentran alineados en la fila 5 para evitar solapamientos con el HUD vanilla.
   - **Identidad de Jugador (Perfil)**: Alineado de manera uniforme en el slot 37 de la Fila 5 en todos los menús (`/sync`, `/matriz` y `/nexo`).
2. **Icono Central de Nexo / Estate Centrado**:
   - El bloque de resumen (3x2) se desplazó 1 slot a la izquierda (slot 12), quedando perfectamente centrado entre los botones 2x2 de Invitar y Unirse.
3. **Adaptación Completa a Menús Vanilla**:
   - Sistema de fallbacks semánticos nativos cuando el jugador ingresa sin Resource Pack (Escudo, Faro, Nether Star, Atril, Repetidor, Cofre, TNT, etc.), evitando hojas de papel vacías.
4. **Submenus de 27 Slots**:
   - Fondos horneados extendidos a 85 px de altura para cubrir holgadamente la fila inferior sin dejar huecos en el contenedor.
5. **Fuente Vanilla**:
   - Tipografia limpia de Minecraft en inventarios para maxima legibilidad.

---

## Instrucciones de Instalacion en Servidor Remoto

1. **Subir el Plugin**:
   - Colocar `dreamcraft-protection-0.1.2.jar` en la carpeta `plugins/` del servidor. Eliminar cualquier JAR anterior (`0.1.1` o `0.1.0`).
2. *+Subir las Configuraciones**:
   - Colocar `config.yml`, `messages.yml` y `presentation-assets.yml` en la carpeta `plugins/DreamCraftProtection/`.
3. **Configurar el Resource Pack**:
   - Subir `dreamcraft-resource-pack-0.1.2-dc83d6cb.zip` a su servidor web o CDN y configurar `server.properties` o el servicio de packs con el hash SHA1: `dc83d6cb8c24b47da2241e8e857fbc9670b8dce0`.
4. **Reiniciar**:
   - Reiniciar el servidor por completo para cargar el nuevo jar y sus recursos.
