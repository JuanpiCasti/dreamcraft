# Despliegue en producción · Configuración dedicada por servidor

> **Audiencia:** admins del servidor oficial DreamCraft y operadores de
> servidores que distribuyan el plugin.
> El JAR es **neutro**: toda identidad de servidor vive en archivos de
> configuración que van **junto al JAR**, nunca dentro de él.

---

## 1. Modelo de 3 capas

| Capa | Contenido | Quién lo define |
|---|---|---|
| **JAR** | Código + defaults embebidos (`config.yml`, `messages.yml`, `presentation-assets.yml`) con vocabulario canónico Ward/Ciudad/Estate | distribución |
| **config.yml** del servidor | Raíces visibles (`command-names`), aliases de subcomandos, selector de menús, banda de sigilo, regeneración de zona | cada servidor |
| **messages.yml** del servidor *(opcional)* | Rebranding total: prefijos, help blocks, títulos de menús, textos del ítem | cada servidor |

Resolución de cualquier texto: **override del servidor → default embebido → fallback en código**.
Los placeholders `{cmd.ward}`, `{cmd.city}`, `{cmd.estate}` se resuelven contra
`command-names`, así que un cambio de término se propaga a TODA la experiencia
sin tocar código ni catálogo.

---

## 2. Qué va aparte del .jar (servidor oficial)

### 2.1 `commands.yml` — raíces versionadas ⚠️ obligatorio

Ubicación: **raíz del servidor** (junto a `bukkit.yml`, NO en `plugins/`).
Bukkit no lo recarga en caliente → requiere reinicio.

```yaml
aliases:
  sync:      [ward $1-]
  sincronia: [ward $1-]
  nexo:      [estate $1-]
  matriz:    [city $1-]
  proteccion:[protection $1-]
```

Sin este archivo `/sync` y `/nexo` simplemente no existen. Plantilla lista:
`plugin-configs/DreamCraftProtection/commands.example.yml`.
(El plugin además registra estos nombres como comandos reales leyendo
`command-names` — el alias de `commands.yml` queda como documentación/redundancia.)

### 2.2 `plugins/DreamCraftProtection/config.yml`

Se copia desde el repo vía `config-sync` (ver §3). Claves que definen la
identidad y el comportamiento de este servidor:

```yaml
# Raíces visibles — deben coincidir con commands.yml
command-names:
  ward: sync
  city: matriz
  estate: nexo
  protection: proteccion

# Vocabulario del lore como aliases de subcomando
commands:
  ward:
    subcommands:
      create:     { aliases: [despertar] }
      rename:     { aliases: [renombrar] }
      upkeep:     { aliases: [] }        # alimentar = subcomando propio remoto VIP
      score:      { aliases: [fase] }
      give:       { aliases: [dar] }
      delete:     { aliases: [apagar] }
      menu:       { aliases: [nucleo] }
  protection:
    subcommands:
      reload:     { aliases: [recargar] }
```

Otras claves de producción ya activas en este repo: `menus.provider`,
`estate-instances.band-below/band-above/protect-structure/regenerate-zone`,
`ward-upgrade-costs`, `city-levels`.

### 2.3 `plugins/DreamCraftProtection/messages.yml` — rebranding (recomendado)

Copia del embebido adaptada al lore: prefijos `[Sincronía]/[Nexo]`, help
blocks completos con sintaxis `/sync …`, títulos de menús
(`Núcleo/Matriz/Nexo`), nombre e iconografía del ítem físico
(`items.nucleus-name`). Versión lista en
`plugin-configs/DreamCraftProtection/messages.yml`.

### 2.4 `plugins/DreamCraftProtection/presentation-assets.yml` *(opcional)*

Contrato con el Resource Pack (CMDs por icono, sonidos, fuentes). Solo si el
servidor remapea los CMDs provisionales. Documento: `docs/presentation-assets.md`.

### 2.5 LuckPerms — permisos de rango

| Grupo | Permisos |
|---|---|
| VIP | `dreamcraft.ward.menu`, `dreamcraft.ward.remote` |
| Staff/mod | anteriores + `dreamcraft.ward.admin`, `dreamcraft.city.admin`, `dreamcraft.protection.admin`, `dreamcraft.integrations.status` |

Sin `dreamcraft.ward.remote` los jugadores normales NO pueden alimentar,
mejorar ni hacer `/sync tp` a distancia (presencia física obligatoria).

---

## 3. Flujo oficial (docker-compose)

```text
repo/plugin-configs/DreamCraftProtection/*  ──config-sync──▶  data/plugins/DreamCraftProtection/
repo/build/libs/dreamcraft-protection-*.jar  ──volume ro────▶  /data/plugins/DreamCraftProtection.jar
data/commands.yml                            ──manual────────▶  raíz del servidor (una sola vez)
```

Orden de actualización:

1. `./gradlew build` (tests incluidos).
2. Copiar el bloque de aliases a `data/commands.yml` si aún no está.
3. `docker compose up -d` (config-sync copia configs; el volumen monta el jar).
4. Reiniciar si solo cambió `commands.yml` (Bukkit no lo recalienta).

---

## 4. Verificación post-arranque

| Check | Esperado |
|---|---|
| Log `[DreamCraft Integrations]` | ✓ en WG/LP/CP/Essentials/WE/Chunky/packetevents |
| Log `[DreamCraft] Comando versionado registrado: /sync → mecánica /ward` | presente |
| `/protection integrations` | infra + presentación (modo assets, proveedor vanilla-cmd, iconos > 0) |
| `/sync` (tab) | sugerencias con vocabulario del lore |
| `/sync despertar` → `/sync alimentar deposit diamond 8` lejos del bloque | rechazo por presencia física |
| Ítem del Núcleo | nombre "Núcleo de Sincronía", menú sin sufijo `DC:` |
| Instancia End | dragona activa (circula, ataca crystals) |

---

## 5. Receta para OTRO servidor (distribución)

Mínimo viable — el plugin funciona out-of-the-box con `/ward`:

```yaml
# commands.yml (si desean renombrar)
parcela: ward $1-

# config.yml
command-names:
  ward: parcela

# messages.yml  (opcional: rebranding completo o dejar el default neutro)
```

Todo lo demás (protección, upkeep, nexos, regeneración) funciona idéntico;
cada servidor decide terminología, textos y CMDs del resource pack sin tocar
el JAR.
