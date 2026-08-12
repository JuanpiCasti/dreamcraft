por ahora para levantarlo en tu compu solo con `docker compose up` funca.

## Estructura

- `docker-compose.yml`: definición del server (versión, plugins, memoria, seed).
- `data/`: todo lo que genera el server (mundos, logs, bases de datos de los plugins, jars descargados). No se trackea en git — se regenera solo con `docker compose up`.
- `plugin-configs/`: los configs de los plugins que editamos a mano. Esto sí se trackea en git.

Al levantar el server, un servicio (`config-sync`) copia automáticamente todo lo que hay en `plugin-configs/` sobre `data/plugins/`, así que el config trackeado siempre pisa al generado.

## Cómo agregar un plugin nuevo

1. Sumá la URL del `.jar` a la lista `PLUGINS` del servicio `mc` en `docker-compose.yml`.
2. Levantá el server una vez para que el plugin genere su config por defecto:
   ```
   docker compose up
   ```
3. Copiá el config generado a la carpeta trackeada, respetando la misma ruta relativa que tiene adentro de `data/plugins/`:
   ```
   mkdir -p plugin-configs/<NombreDelPlugin>
   cp data/plugins/<NombreDelPlugin>/config.yml plugin-configs/<NombreDelPlugin>/config.yml
   ```
4. Editá `plugin-configs/<NombreDelPlugin>/config.yml` a gusto.
5. La próxima vez que levantes el server, `config-sync` aplica ese config solo. No hace falta tocar `docker-compose.yml` para esto.

## Cómo modificar el config de un plugin existente

- Si lo editás directamente en `plugin-configs/<Plugin>/config.yml`, se aplica solo en el próximo `docker compose up`.
- Si lo cambiás en el juego (con comandos) o directo en `data/`, para que no se pierda cuando se recree el volumen tenés que copiarlo a mano a `plugin-configs/`.

## Qué se trackea y qué no

- **Se trackea**: `docker-compose.yml`, `plugin-configs/` (configs a mano de los plugins).
- **No se trackea** (`data/` está en `.gitignore`): mundos, logs, bases de datos de los plugins (ej. CoreProtect, LuckPerms, playerdata de nLogin), datos de jugadores (`userdata/` de Essentials), regiones de WorldGuard. Todo eso es estado del server o se regenera solo, no es config a mano.

## Pregeneración de mundo con Chunky

El plugin [Chunky](https://modrinth.com/plugin/chunky) ya está agregado en `docker-compose.yml` y su config trackeada en `plugin-configs/Chunky/` (con `continue-on-restart: true`, así retoma la tarea sola después de un restart o de que se actualice la imagen).

Lo único manual es arrancar la pregeneración una vez, desde la consola del server:

```
chunky start world square 0 0 6000
chunky start world_nether square 0 0 750
chunky start world_the_end square 0 0 1000
```

Para mandar estos comandos desde afuera del contenedor:

```
docker compose exec mc rcon-cli "chunky start world square 0 0 6000"
```

Para ver el progreso: `docker compose exec mc rcon-cli "chunky progress"`.

Esto genera el mundo (12000×12000), el nether (1500×1500) y el end (2000×2000), todo centrado en el spawn. No hace falta repetir los comandos después de un restart — `continue-on-restart` se encarga.
