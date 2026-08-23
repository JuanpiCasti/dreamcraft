# Artefactos y configuración de referencia — DreamCraftProtection

Copia de los archivos importantes para instalar/revisar el plugin sin
necesidad de compilar. Se regeneran con `.\gradlew.bat build` + este paso.

## Contenido

| Archivo | Origen | Qué es |
|---|---|---|
| `dreamcraft-protection-0.1.1.jar` | `build/libs/` | Plugin compilado, release sin SNAPSHOT (Paper 1.21.x). Soltar en `plugins/` del server. |
| `plugin.yml` | `build/resources/main/plugin.yml` | Manifiesto que sale del BUILD (con la versión ya expandida a `0.1.1`, no el template de `src/main/resources/`): comandos (`/protection`, `/ward` `/sync`, `/city` `/matriz`, `/estate` `/nexo`) y permisos. |
| `config.yml` | `plugin-configs/DreamCraftProtection/config.yml` | Config efectiva desplegada al server (incluye sección `ward.recipe` del crafteo configurable). |
| `messages.yml` | `plugin-configs/DreamCraftProtection/messages.yml` | Overrides de mensajes (vocabulario «El Despertar») aplicados sobre los embebidos. |

## Notas

- Los YAML de esta carpeta son la **fuente de verdad desplegada** (los que
  copia el contenedor `config-sync` a `data/plugins/DreamCraftProtection/`).
  Los equivalentes embebidos en el jar actúan solo como defaults.
- El jar del arnés de pruebas vive en `harness/build/libs/` y no se incluye
  acá porque es infraestructura de test, no artefacto de instalación.
- Suite de verificación: `powershell -ExecutionPolicy Bypass -File run-tests.ps1`.

## Subir a producción (servidor remoto)

Esta carpeta **es el paquete de la release**: subir SIEMPRE los tres archivos
juntos (jar + config.yml + messages.yml).

1. Subir `dreamcraft-protection-0.1.1.jar` a `plugins/` del server remoto,
   reemplazando el jar `0.1.0` anterior. Si queda un jar con otro nombre de la
   versión vieja, borrarlo para no cargar dos versiones.
2. Subir `config.yml` y `messages.yml` a `plugins/DreamCraftProtection/` del
   server remoto. Subir solo el jar no rompe nada (las claves nuevas caen a
   defaults en código), pero los overrides viejos de mensajes dejarían textos
   desactualizados; por eso se suben los dos ymls de acá.
3. El `commands.yml` de la raíz del server NO cambia en esta versión
   (sin alias nuevos).
4. Reiniciar el server COMPLETO (no vale `/reload` ni plugman).

### Verificación post-arranque

- Log `[DreamCraft] Comando versionado registrado: /sync` presente.
- `/protection integrations` lista infra y presentación sin errores.
- Prueba del fix estrella: colocar el Núcleo/Ward junto a una mesa de
  encantamientos YA NO devuelve el bloque; en `/sync upkeep` (o menú del
  núcleo) aparece el sobrecosto por bloque bajo tier
  (`ward.below-tier-surcharge-units`, default 2).
- Menús con vocabulario del lore y textos de messages.yml actualizados.
