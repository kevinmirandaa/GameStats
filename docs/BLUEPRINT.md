# GameStats Blueprint v1.0

Fuente de verdad del proyecto. Todo agente debe leer este archivo antes de trabajar. Si algo aqui cambia, se registra en `docs/DECISIONS.md`.

## 1. Proposito

GameStats recibe eventos de partidas de videojuegos, los valida y organiza (Go, imperativo), y los transforma en estadisticas y patrones (Scala, funcional). Es independiente de quien genera los juegos.

Entrega: domingo 18 de octubre de 2026, antes de las 10:00 pm, por el Tec Digital. Trabajo individual.

## 2. Fuera de alcance

- Implementar videojuegos, motor grafico o interfaz de juego.
- RabbitMQ, Kafka, Kubernetes, bases de datos externas.
- La futura consola de juegos. Solo se deja el diseno preparado para recibir juegos nuevos.

## 3. Arquitectura

```
SIMULADOR (Go)  o  JUEGO EXTERNO (cualquier lenguaje)
      |
      |  POST /events  o  POST /events/batch   (JSON, contrato de evento)
      v
MODULO GO  ingest-go  :8080
  API HTTP -> Validador -> Deduplicador -> Router por matchId
  Una goroutine por partida con su estado (jugadores, eventos ordenados)
  Cierre de partida: MATCH_FINISHED o inactividad
  Rechazados -> registro de eventos invalidos
      |
      |  POST /analyze   (MatchBatch: partida completa y ordenada)
      v
MODULO SCALA  analytics-scala  :8081
  Decoder -> List[Event] inmutable
  Analisis generico (todos los juegos)
  Analizadores por juego (racing, combat, futuros)
  Reglas de secuencia (patrones)
  Almacen en memoria de resultados
      |
      |  GET /stats/...   (JSON)
      v
CONSULTA (curl, navegador, futura interfaz)
```

Despliegue: Docker Compose con tres servicios: `simulator`, `ingest-go`, `analytics-scala`.

## 4. Tecnologias

- Go 1.22, solo libreria estandar (net/http, encoding/json, sync, testing).
- Scala 3 (LTS 3.3.x), sbt, Cask para HTTP, upickle para JSON, munit para pruebas.
- Docker y Docker Compose.
- GitHub, GitHub Actions, GitHub Container Registry.
- Formato de intercambio: JSON. Validacion formal: JSON Schema en `contracts/`.

## 5. Contrato de evento (schemaVersion 1.0)

Campos del sobre comun:

- `schemaVersion` texto, obligatorio, hoy "1.0".
- `eventId` texto, obligatorio, unico globalmente.
- `timestamp` texto RFC 3339 en UTC, obligatorio.
- `gameId` texto, obligatorio, por ejemplo "racing", "combat".
- `gameVersion` texto, obligatorio.
- `matchId` texto, obligatorio, unico globalmente.
- `playerId` texto, obligatorio para PLAYER_JOINED, PLAYER_LEFT y GAME_ACTION. Puede faltar en MATCH_STARTED y MATCH_FINISHED. Unico globalmente.
- `type` obligatorio, uno de: MATCH_STARTED, MATCH_FINISHED, PLAYER_JOINED, PLAYER_LEFT, GAME_ACTION.
- `action` texto, obligatorio solo si type es GAME_ACTION. Es abierto: cada juego define las suyas.
- `data` objeto libre, por defecto vacio. Go nunca valida su contenido.

Reglas de genericidad:

1. Go valida solo el sobre. El contenido de `data` y el valor de `action` son responsabilidad del analizador del juego.
2. Un `action` desconocido se acepta y cuenta en las estadisticas genericas.
3. Un juego nuevo no requiere cambios en Go.
4. `MATCH_FINISHED.data` puede incluir `winnerPlayerId` y `ranking` (lista ordenada de playerId).

Ejemplo:

```json
{
  "schemaVersion": "1.0",
  "eventId": "evt-000123",
  "timestamp": "2026-10-12T18:20:15.250Z",
  "gameId": "racing",
  "gameVersion": "1.0",
  "matchId": "match-racing-007",
  "playerId": "player-03",
  "type": "GAME_ACTION",
  "action": "LAP_COMPLETED",
  "data": { "lap": 2, "lapTimeMs": 91250, "position": 4, "topSpeedKmh": 287.4 }
}
```

## 6. Contratos REST

### Go (ingest-go, puerto 8080)

- `POST /events` cuerpo: un evento. Respuestas: 202 aceptado, 400 con lista de errores de validacion, 409 si eventId duplicado.
- `POST /events/batch` cuerpo: lista de eventos. Respuesta 200 con `{accepted, rejected, errors:[{eventId, reasons}]}`.
- `GET /health` respuesta 200 `{status:"UP"}`.
- `GET /metrics` respuesta con contadores: recibidos, aceptados, rechazados, partidas activas, partidas enviadas.
- `GET /rejected` ultimos eventos rechazados con sus motivos.

Configuracion por variables de entorno: `PORT`, `ANALYTICS_URL`, `MATCH_IDLE_TIMEOUT_SECONDS`, `REJECTED_EVENTS_PATH`.

### Go hacia Scala

`POST {ANALYTICS_URL}/analyze` cuerpo MatchBatch:

```json
{
  "schemaVersion": "1.0",
  "gameId": "racing",
  "gameVersion": "1.0",
  "matchId": "match-racing-007",
  "status": "COMPLETED",
  "events": [ "..." ]
}
```

`status` es COMPLETED si llego MATCH_FINISHED, o INCOMPLETE si se cerro por inactividad. `events` va ordenado por timestamp y sin duplicados. Respuesta de Scala: 200 con MatchAnalysis. Si Scala falla, Go reintenta con espera creciente y registra el fallo.

### Scala (analytics-scala, puerto 8081)

- `POST /analyze` recibe MatchBatch, guarda y devuelve MatchAnalysis.
- `GET /stats/matches` lista resumida de partidas analizadas, filtro opcional `?gameId=`.
- `GET /stats/matches/{matchId}` MatchAnalysis completo.
- `GET /stats/players/{playerId}` agregado del jugador en todas sus partidas.
- `GET /stats/games/{gameId}` agregado del juego.
- `GET /patterns?gameId=&type=` patrones detectados.
- `GET /health`.

MatchAnalysis:

```json
{
  "matchId": "...",
  "gameId": "...",
  "status": "COMPLETED",
  "durationSeconds": 0.0,
  "totalEvents": 0,
  "eventCountsByType": { "GAME_ACTION": 0 },
  "eventCountsByAction": { "LAP_COMPLETED": 0 },
  "matchMetrics": { "nombreMetrica": 0.0 },
  "players": [
    { "playerId": "...", "eventCount": 0, "metrics": { "nombreMetrica": 0.0 } }
  ],
  "patterns": [
    { "name": "COMEBACK", "playerId": "...", "startEventId": "...", "endEventId": "...", "details": { } }
  ]
}
```

Las metricas son mapas texto a numero para que un juego nuevo agregue metricas sin cambiar el modelo.

## 7. Responsabilidad de Go

Recibir, validar, deduplicar, clasificar por partida, mantener estado temporal, procesar partidas en paralelo con goroutines y channels, cerrar partidas, ordenar por timestamp, enviar a Scala, registrar rechazados. Go no calcula estadisticas.

## 8. Responsabilidad de Scala

Decodificar, transformar colecciones con filter, map, flatMap, groupBy, fold, reduce, collect, sliding y composicion de funciones. Calcular metricas, detectar patrones, producir JSON, guardar y servir resultados. Todo el dominio es inmutable y las funciones son puras. Los efectos (HTTP, almacenamiento) quedan en la capa externa.

Extensibilidad: un trait `GameAnalyzer` con `gameId` y `analyze(events): GameInsights`, y un registro de analizadores por gameId. El analisis generico corre siempre. Agregar un juego es agregar un archivo y registrarlo.

## 9. Juegos iniciales

Racing y Combat. El detalle de acciones, metricas y reglas esta en `docs/STATS.md` (lo produce el agente de estadisticas). Estrategia queda como tercer dominio opcional si sobra tiempo.

## 10. Preparado para juegos reales futuros

Un juego nuevo, en cualquier lenguaje (incluido Prolog), solo necesita enviar eventos con el contrato de la seccion 5. Para que sus estadisticas especificas existan se agrega un analizador en Scala y su documento en `contracts/games/{gameId}.md`. Los pasos estan en `docs/ADDING_A_GAME.md`.

## 11. Estructura del repositorio

- `contracts/` esquema JSON del evento, ejemplos y `games/` con un documento por juego.
- `ingest-go/` modulo Go de ingesta.
- `analytics-scala/` modulo Scala de analisis.
- `simulator/` modulo Go que genera partidas simuladas.
- `deploy/` docker-compose y archivos de despliegue.
- `.github/workflows/` pipelines CI/CD.
- `docs/` documentacion.
- `agents/` agentes de IA, `prompts/` instrucciones por fase, `reports/` resumenes que entregan los agentes.
- `project/` documentos originales del curso.

## 12. Flujo de trabajo Git

- Rama `main` siempre estable. Una rama por fase: `feature/fase-NN-nombre`.
- Commits pequenos con prefijos: `feat`, `fix`, `docs`, `test`, `build`, `ci`, `refactor`.
- Cada fase termina en un Pull Request.
- Antes de unir: el agente QA ejecuta la compuerta de calidad de la fase (`prompts/qa-gate.md`). Solo si aprueba se hace el merge.
- Kevin ejecuta los comandos Git. Se explica la primera vez y despues los hace solo.

## 13. Fases

- Fase 00 Fundacion: estructura, git, contratos.
- Fase 01 Diseno de estadisticas: `docs/STATS.md` y contratos de juego.
- Fase 02 Go ingesta.
- Fase 03 Scala nucleo y API.
- Fase 04 Scala analizadores racing y combat.
- Fase 05 Simulador.
- Fase 06 Integracion con Docker Compose.
- Fase 07 QA integral.
- Fase 08 CI/CD y despliegue.
- Fase 09 Documentacion y demo.

## 14. Estandares de codigo

Ver `agents/shared/CODE_STANDARDS.md`. Resumen: codigo limpio, nombres largos y descriptivos, sin comentarios, sin emojis, sin lineas decorativas, funciones pequenas con una sola responsabilidad.
