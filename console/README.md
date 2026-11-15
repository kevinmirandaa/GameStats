# GameConsole

Consola web de videojuegos en Java 21. Carga juegos como plugins, ejecuta varias partidas en paralelo, envia sus eventos a GameStats y muestra las estadisticas que GameStats calcula.

## Modulos

| Modulo | Contenido |
|---|---|
| `console-api` | Interfaces comunes: `GameModule`, `GameSession`, `GameObserver`, `LogicEngine` y la base `ObservableGameSession` |
| `game-connect4` | Conecta 4. La computadora decide con Prolog |
| `game-memory` | Memoria de parejas para uno o dos jugadores |
| `game-minesweeper` | Buscaminas |
| `console-server` | Servidor web, cargador de plugins, sesiones, publicador de eventos y proxy de estadisticas |

## Ejecutar

Con Docker (desde la raiz del proyecto):

```bash
./instalar.sh
```

Abre `http://localhost:8100`. El panel de estadisticas esta en `http://localhost:8100/#panel`.

## Variables

| Variable | Valor por defecto |
|---|---|
| `PORT` | 8100 |
| `PLUGINS_DIRECTORY` | plugins |
| `GAMESTATS_INGEST_URL` | http://localhost:8080 |
| `GAMESTATS_ANALYTICS_URL` | http://localhost:8081 |
| `PROLOG_URL` | http://localhost:8090 |
| `IDLE_SESSION_MINUTES` | 20 |
| `DELIVERY_MAX_ATTEMPTS` | 14 |
| `PROLOG_MAX_CONCURRENCY` | 4 |

## API

| Ruta | Funcion |
|---|---|
| `GET /api/games` | Catalogo de juegos cargados |
| `POST /api/sessions` | Inicia una partida (`gameId`, `mode`, `players`, `options`) |
| `GET /api/sessions/{matchId}` | Estado de una partida |
| `POST /api/sessions/{matchId}/actions` | Jugada (`playerId`, `action`) |
| `DELETE /api/sessions/{matchId}` | Abandona la partida |
| `GET /api/stats/overview` | Estadisticas generales de GameStats |
| `GET /api/stats/games/{gameId}` | Estadisticas de un juego |
| `GET /api/stats/live` | Instancias en curso y eventos aceptados |
| `GET /api/delivery` | Estado del envio de eventos |

## Agregar un juego

1. Crear un modulo que dependa de `console-api` con `provided`.
2. Implementar `GameModule` y una `GameSession` (extender `ObservableGameSession`).
3. Declarar el modulo en `META-INF/services/gameconsole.api.GameModule`.
4. Copiar el JAR a la carpeta de plugins y reiniciar la consola.

Para tener estadisticas propias, agregar un analizador Scala en GameStats (ver `docs/ADDING_A_GAME.md`).

## Pruebas

```bash
mvn -B verify
```

o con Docker, sin instalar Java ni Maven: `make test-console`.
