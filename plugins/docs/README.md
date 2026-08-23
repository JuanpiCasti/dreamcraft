# Artefactos y configuración de referencia — DreamCraftProtection

Copia de los archivos importantes para instalar/revisar el plugin sin
necesidad de compilar. Se regeneran con `.\gradlew.bat build` + este paso.

## Contenido

| Archivo | Origen | Qué es |
|---|---|---|
| `dreamcraft-protection-0.1.1-SNAPSHOT.jar` | `build/libs/` | Plugin compilado (Paper 1.21.x). Soltar en `plugins/` del server. |
| `plugin.yml` | `src/main/resources/plugin.yml` | Manifiesto: comandos (`/protection`, `/ward` `/sync`, `/city` `/matriz`, `/estate` `/nexo`) y permisos. |
| `config.yml` | `plugin-configs/DreamCraftProtection/config.yml` | Config efectiva desplegada al server (incluye sección `ward.recipe` del crafteo configurable). |
| `messages.yml` | `plugin-configs/DreamCraftProtection/messages.yml` | Overrides de mensajes (vocabulario «El Despertar») aplicados sobre los embebidos. |

## Notas

- Los YAML de esta carpeta son la **fuente de verdad desplegada** (los que
  copia el contenedor `config-sync` a `data/plugins/DreamCraftProtection/`).
  Los equivalentes embebidos en el jar actúan solo como defaults.
- El jar del arnés de pruebas vive en `harness/build/libs/` y no se incluye
  acá porque es infraestructura de test, no artefacto de instalación.
- Suite de verificación: `powershell -ExecutionPolicy Bypass -File run-tests.ps1`.
