# Testing local de DreamCraftProtection

Sistema para detectar regresiones y gaps después de cada cambio del plugin,
sin subir nada a un server online. Dos capas:

| Capa | Alcance | Ejecución |
|---|---|---|
| **A. Unitaria** | Dominio (Ward/City/Estate), viewmodels, menús, mensajes, config — sin Bukkit runtime | `gradlew build` la corre siempre (`src/test/java`) |
| **B. Integración real** | Comandos reales despachados con permisos controlados, registro de raíces versionadas, tab-completer, config desplegada, persistencia | Plugin-arnés dentro del Docker existente |

## Uso

```powershell
powershell -ExecutionPolicy Bypass -File run-tests.ps1            # todo (unitaria + integración)
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -UnitOnly  # solo capa A
powershell -ExecutionPolicy Bypass -File run-tests.ps1 -NoReset   # conserva wards/ciudades/estates actuales
```

Exit codes: `0` OK · `1` fallos · `2` infraestructura (timeout/boot).

Flujo del paso 3-4: resetea fixtures → `docker compose up --force-recreate mc`
→ el arnés corre solo tras el boot (~300 ticks) → escribe
`data/dreamcraft-test/results.json` y `report.txt` → el script imprime el
resumen. También se puede relanzar sin reiniciar vía RCON: `dctest run`.

## Cómo funciona el arnés

`harness/` es un subproyecto Gradle que produce `DreamCraftTestHarness.jar`,
montado como segundo plugin en el server. Es **black-box**: solo depende de la
API de Bukkit, nunca de clases de DreamCraftProtection.

- **Personas** (`Persona.java`): perfiles de usuario con permisos exactos
  (`visitante`, `jugadorBasico`, `jugadorVip`, `adminJugador`, `consola`).
  Los senders son proxies que capturan cada mensaje recibido.
- **Despacho**: Paper 26 exige que todo sender sea convertible a un stack de
  vanilla (`VanillaCommandWrapper.getListener`), así que `Bukkit.dispatchCommand`
  no admite senders sintéticos. Los comandos propios se invocan por
  `PluginCommand.execute()` (mismo gate de permisos + executor que la ruta
  real); comandos de terceros caen a consola real.
- **Escenarios ASSERTED**: contrato definido — fallan si el comportamiento
  cambia (regresiones). Ej.: propiedad de `/sync` tras el boot (regresión del
  alias de commands.yml), tab-completer nivel 1, rechazo de consola.
- **Escenarios PROBE**: registran comportamiento observado **sin asumirlo
  correcto** → sección "GAPS CANDIDATOS" del reporte. Al definir el contrato,
  se convierten en ASSERTED.

## Límites conocidos

- Flujos que exigen una entidad Player real (abrir/clickear menús, colocar
  bloques) no son simulables desde API pura; quedan en verificación manual o
  fase futura (cliente headless).
- La salida de comandos externos (LuckPerms, etc.) va al log del server, no al
  reporte.

## Agregar escenarios

Editar `harness/src/main/java/dev/dreamcraft/harness/Scenarios.java`,
añadir `Scenario.asserted(...)` o `Scenario.probe(...)` y correr la suite.
Las expectativas se anclan en frases estables de `messages.yml`: si cambia el
vocabulario, actualizar la expectativa (el texto es parte del contrato).

## Gaps detectados y su estado

1. ~~Toda la capa de comando exige `Player`~~ → **RESUELTO (v2)**: consola/RCON
   ya ejecuta las ops admin sin ubicación — `proteccion reload`,
   `proteccion integrations`, `nexo disband <id>`, `nexo admin reset <id>`.
   El resto de subcomandos conserva el contrato players-only (los cubre
   `consola-rechaza-comando-jugador`). Contrato vigente en
   `consola-proteccion-reload`, `consola-proteccion-integraciones` e
   `nexo-disband-id-inexistente`.
2. "Mensaje duplicado en `nexo disband`" → **falso positivo**: era sangrado de
   estado del propio arnés (buffers reutilizados entre escenarios). Corregido:
   cada dispatch construye un sender fresco; el escenario ahora verifica una
   única ocurrencia del error.

### Gaps abiertos

1. Flujos que exigen entidad Player real (menús clickeables, colocación de
   bloques, `proteccion give`, creación de wards) siguen fuera del alcance v1.
   Con el contrato v3 se suman dos dependientes de entidad real:
   `/sync reclamar` (entrega de ítem al inventario) y la disolución con
   devolución del núcleo (comando, menú o rotura del bloque). La receta sí
   queda cubierta black-box por `receta-nucleo-registrada-taggeada` (PDC).
2. La salida de comandos externos (LuckPerms, etc.) va al log del server, no
   al reporte.
