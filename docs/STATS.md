# Estadisticas, juegos y reglas

Define los dos juegos de referencia (racing y combat), sus eventos, metricas y reglas de patron. Es la referencia de Scala, del simulador y de las pruebas.

Datos de ejemplo: `contracts/examples/games/{juego}/`. Resultados esperados: `docs/STATS_EXPECTED_RESULTS.md` y los archivos `*.expected.json` junto a cada partida.

## 1. Convenciones generales

### Nombres

- Acciones: `UPPER_SNAKE_CASE`.
- Metricas y campos de `data`: `camelCase`.
- Unidades en el nombre cuando aplica: `Ms`, `Seconds`, `Kmh`, `Meters`, `Percent`.

### Calculo

- Los eventos se procesan en orden de `timestamp`, con empate resuelto por orden de llegada. Go ya los entrega ordenados.
- Una metrica solo aparece en el resultado si esta definida para ese jugador o partida. Una metrica no definida se omite, nunca se rellena con cero ni con NaN. Los conteos y sumas siempre estan definidos y valen 0 si no hay eventos.
- Todos los valores se devuelven como numeros. Los resultados esperados se redondean a 3 decimales. Las pruebas comparan con tolerancia de 0.001.
- `durationSeconds` es la diferencia entre el timestamp del ultimo y del primer evento de la partida.
- `eventCount` de un jugador es la cantidad de eventos cuyo `playerId` es ese jugador. Un jugador que solo aparece como victima no suma eventos por serlo.
- La lista de jugadores de una partida es la union de los `playerId` de los eventos y de los identificadores de jugador mencionados en `data` que el analizador del juego declare como participantes (en combat, `victimPlayerId`). Se ordena por `playerId`.
- PLAYER_LEFT no modifica ninguna metrica ni regla. El jugador sigue figurando como participante con los eventos que emitio antes de salir.
- Los patrones se ordenan por la posicion del evento `startEventId` dentro de la partida y, en empate, por nombre.
- `details` de un patron contiene solo numeros.

### Partidas incompletas

Si el estado es INCOMPLETE (sin MATCH_FINISHED): no se calculan `finalPosition`, `positionsGained` ni `finishedPlayers` vale 0, y no se detectan las reglas que dependen del ganador (RACE_COMEBACK y SCORE_COMEBACK). El resto se calcula normalmente.

### Agregados entre partidas

Los calcula el nucleo de Scala de forma generica, sin conocer el juego.

Agregado de jugador (`GET /stats/players/{playerId}`):

- `matchesPlayed`: partidas donde participo.
- `wins`: partidas donde `MATCH_FINISHED.data.winnerPlayerId` es el jugador.
- Para cada metrica presente: `mean_{metrica}`, `min_{metrica}` y `max_{metrica}`, calculados sobre las partidas donde esa metrica esta definida.
- `patternCount_{NOMBRE}` por cada patron detectado a ese jugador.

Agregado de juego (`GET /stats/games/{gameId}`):

- `matchesAnalyzed`, `matchesCompleted`.
- Para cada metrica de partida: `mean_{metrica}`, `min_{metrica}` y `max_{metrica}`.
- `patternCount_{NOMBRE}` por cada patron.

Con esto, mejor vuelta historica es `min_fastestLapMs` y mayor cantidad de eliminaciones de un jugador en una partida es `max_mostEliminationsByOnePlayer`.

### Cumplimiento del enunciado

| Requisito | Racing | Combat |
|---|---|---|
| Estadistica por jugador | bestLapMs, lapTimeStdDevMs, overtakesMade | eliminations, killDeathRatio, accuracyPercent |
| Estadistica por partida | fastestLapMs, leadChanges | totalEliminations, eliminationsPerMinute |
| Combina tipos de evento | effectiveRaceTimeMs, positionsGained | damagePerElimination |
| Regla por secuencia | RACE_COMEBACK, IMPROVING_LAP_STREAK | KILL_STREAK, SCORE_COMEBACK |

## 2. Racing

### Justificacion

Un juego de carreras genera datos numericos continuos (tiempos, velocidades, posiciones) que permiten medir consistencia y evolucion, no solo contar. Sus secuencias naturales son la mejora vuelta a vuelta y la remontada de posiciones.

### Eventos del ciclo de vida

| type | playerId | data |
|---|---|---|
| MATCH_STARTED | no | track (texto), totalLaps (entero) |
| PLAYER_JOINED | piloto | displayName (texto), vehicle (texto), startPosition (entero desde 1) |
| MATCH_FINISHED | no | winnerPlayerId (texto), ranking (lista de playerId, primero es el ganador) |

PLAYER_LEFT se usa cuando un piloto abandona. Data libre, por ejemplo reason.

### Acciones (type GAME_ACTION)

| action | playerId | data |
|---|---|---|
| LAP_COMPLETED | piloto | lap (entero desde 1), lapTimeMs (entero, milisegundos), position (entero, posicion al cruzar la meta), topSpeedKmh (numero) |
| POSITION_CHANGED | piloto cuya posicion cambia | lap (entero), fromPosition (entero), toPosition (entero) |
| PENALTY_APPLIED | piloto penalizado | lap (entero), seconds (numero), reason (texto) |
| PIT_STOP | piloto | lap (entero), durationMs (entero) |

POSITION_CHANGED se emite para cada piloto afectado en un adelantamiento: el que sube y el que baja.

### Secuencia tipica

MATCH_STARTED, un PLAYER_JOINED por piloto, luego por cada vuelta LAP_COMPLETED de cada piloto con POSITION_CHANGED, PENALTY_APPLIED y PIT_STOP intercalados, y finalmente MATCH_FINISHED.

### Metricas por jugador

| Metrica | Formula | Eventos |
|---|---|---|
| lapsCompleted | cantidad de LAP_COMPLETED del piloto | LAP_COMPLETED |
| bestLapMs | minimo de lapTimeMs | LAP_COMPLETED |
| averageLapMs | media de lapTimeMs | LAP_COMPLETED |
| lapTimeStdDevMs | desviacion estandar poblacional de lapTimeMs (consistencia, menor es mejor) | LAP_COMPLETED |
| topSpeedKmh | maximo de topSpeedKmh | LAP_COMPLETED |
| penaltySecondsTotal | suma de seconds | PENALTY_APPLIED |
| pitStopCount | cantidad de PIT_STOP | PIT_STOP |
| pitStopTotalMs | suma de durationMs | PIT_STOP |
| overtakesMade | cantidad de POSITION_CHANGED con toPosition menor que fromPosition | POSITION_CHANGED |
| effectiveRaceTimeMs | suma de lapTimeMs mas penaltySecondsTotal por 1000 | LAP_COMPLETED y PENALTY_APPLIED |
| finalPosition | indice del piloto en ranking mas 1 | MATCH_FINISHED |
| positionsGained | startPosition menos finalPosition (positivo es ganar puestos) | PLAYER_JOINED y MATCH_FINISHED |

Definidas solo si hay vueltas: bestLapMs, averageLapMs, lapTimeStdDevMs, topSpeedKmh, effectiveRaceTimeMs. Definidas solo si el piloto esta en el ranking y tiene startPosition: finalPosition y positionsGained.

### Metricas por partida

| Metrica | Formula | Eventos |
|---|---|---|
| totalLapsCompleted | cantidad de LAP_COMPLETED | LAP_COMPLETED |
| fastestLapMs | minimo de lapTimeMs entre todos | LAP_COMPLETED |
| averageLapMs | media de todos los lapTimeMs | LAP_COMPLETED |
| penaltyCount | cantidad de PENALTY_APPLIED | PENALTY_APPLIED |
| pitStopCount | cantidad de PIT_STOP | PIT_STOP |
| positionChangesTotal | cantidad de POSITION_CHANGED | POSITION_CHANGED |
| leadChanges | POSITION_CHANGED con toPosition igual a 1 | POSITION_CHANGED |
| finishedPlayers | tamano de ranking | MATCH_FINISHED |

### Reglas de secuencia

#### RACE_COMEBACK

Un piloto que llego a ir al menos cuarto y termino ganando.

- Condicion: el ganador (`winnerPlayerId`) tiene al menos un LAP_COMPLETED con `position` mayor o igual a 4.
- `startEventId`: el primer LAP_COMPLETED del ganador con la peor posicion (la mayor).
- `endEventId`: el MATCH_FINISHED.
- `playerId`: el ganador.
- details: `worstPosition`, `worstLap`, `finalPosition` (siempre 1).
- Umbral: 4 posiciones. Requiere partidas con al menos 4 pilotos para poder dispararse.

#### IMPROVING_LAP_STREAK

Tres o mas vueltas consecutivas de un mismo piloto, cada una estrictamente mas rapida que la anterior.

- Se recorren los LAP_COMPLETED del piloto en orden. Una racha es una subsecuencia consecutiva donde cada lapTimeMs es menor que el anterior. Se reporta cada racha maxima de largo 3 o mas, una sola vez.
- `startEventId`: el LAP_COMPLETED que abre la racha. `endEventId`: el que la cierra.
- details: `streakLength`, `firstLapMs`, `lastLapMs`.
- Tiempos iguales no continuan la racha.

### Metrica de juego

Ver agregados en la seccion 1.

## 3. Combat

### Justificacion

Un juego de combate genera eventos discretos entre dos jugadores (quien elimina a quien), de donde se derivan puntaje, relaciones y momentum. Permite metricas de rendimiento y reglas basadas en el marcador y las rachas.

### Eventos del ciclo de vida

| type | playerId | data |
|---|---|---|
| MATCH_STARTED | no | map (texto), mode (texto) |
| PLAYER_JOINED | jugador | displayName (texto) |
| MATCH_FINISHED | no | winnerPlayerId (texto), ranking (lista de playerId, primero es el ganador) |

### Acciones (type GAME_ACTION)

| action | playerId | data |
|---|---|---|
| ELIMINATION | quien elimina | victimPlayerId (texto), weapon (texto), distanceMeters (numero) |
| DAMAGE_DEALT | quien daña | targetPlayerId (texto), amount (numero), weapon (texto) |
| SHOT_FIRED | quien dispara | weapon (texto), hit (booleano) |

La muerte de un jugador no es un evento propio: se deriva de `victimPlayerId` de una ELIMINATION. Los jugadores solo mencionados como victimas cuentan como participantes.

### Secuencia tipica

MATCH_STARTED, PLAYER_JOINED por jugador, y luego por cada enfrentamiento SHOT_FIRED, DAMAGE_DEALT y ELIMINATION, hasta MATCH_FINISHED.

### Metricas por jugador

| Metrica | Formula | Eventos |
|---|---|---|
| eliminations | ELIMINATION con playerId igual al jugador | ELIMINATION |
| deaths | ELIMINATION con victimPlayerId igual al jugador | ELIMINATION |
| killDeathRatio | eliminations dividido entre el mayor de deaths y 1 | ELIMINATION |
| damageDealtTotal | suma de amount de sus DAMAGE_DEALT | DAMAGE_DEALT |
| damageDealtAverage | damageDealtTotal entre la cantidad de sus DAMAGE_DEALT | DAMAGE_DEALT |
| shotsFired | cantidad de sus SHOT_FIRED | SHOT_FIRED |
| shotsHit | sus SHOT_FIRED con hit verdadero | SHOT_FIRED |
| accuracyPercent | shotsHit entre shotsFired por 100 | SHOT_FIRED |
| damagePerElimination | damageDealtTotal entre eliminations | DAMAGE_DEALT y ELIMINATION |
| longestEliminationDistanceMeters | maximo distanceMeters de sus eliminaciones | ELIMINATION |

Definidas solo si hay datos: damageDealtAverage (al menos un DAMAGE_DEALT), accuracyPercent (al menos un SHOT_FIRED), damagePerElimination y longestEliminationDistanceMeters (al menos una eliminacion).

### Metricas por partida

| Metrica | Formula | Eventos |
|---|---|---|
| totalEliminations | cantidad de ELIMINATION | ELIMINATION |
| totalDamageDealt | suma de amount de todos los DAMAGE_DEALT | DAMAGE_DEALT |
| eliminationsPerMinute | totalEliminations entre (durationSeconds entre 60) | ELIMINATION |
| mostEliminationsByOnePlayer | maximo de eliminations entre los jugadores | ELIMINATION |
| averageEliminationDistanceMeters | media de distanceMeters de todas las eliminaciones | ELIMINATION |

averageEliminationDistanceMeters se define solo si hay eliminaciones.

### Reglas de secuencia

#### KILL_STREAK

Tres o mas eliminaciones seguidas de un jugador sin ser eliminado entre ellas.

- Se recorren las ELIMINATION en orden. Cada jugador lleva un contador: sube en uno cuando elimina y vuelve a cero cuando es la victima de una ELIMINATION. Cuando el contador se reinicia o la partida termina, si valia 3 o mas se reporta la racha.
- `startEventId`: la primera ELIMINATION de la racha. `endEventId`: la ultima.
- details: `streakLength`.
- Umbral: 3.

#### SCORE_COMEBACK

El ganador llego a ir perdiendo por tres o mas eliminaciones frente al jugador que lideraba en ese momento.

- El marcador de cada jugador es la cantidad acumulada de sus eliminaciones. Tras cada ELIMINATION se calcula la desventaja del ganador: el mayor marcador entre los demas menos el marcador del ganador.
- Condicion: la desventaja maxima es 3 o mas.
- `startEventId`: la primera ELIMINATION tras la cual se alcanza esa desventaja maxima. `endEventId`: MATCH_FINISHED. `playerId`: el ganador.
- details: `maxDeficit`, `finalEliminations` (eliminaciones del ganador al terminar).
- Umbral: 3. No aplica a partidas sin ganador declarado.

## 4. Propuesta opcional: Estrategia

Tercer dominio de referencia, solo como propuesta. No tiene escenarios ni se implementa salvo que sobre tiempo.

- gameId: `strategy`.
- Acciones: RESOURCE_GATHERED (resource, amount), BUILDING_COMPLETED (building, durationSeconds), UNIT_TRAINED (unit, cost), OBJECTIVE_COMPLETED (objective, resourcesSpent, durationSeconds).
- Metricas: recursos recogidos por tipo, eficiencia de recursos (valor de objetivos completados entre recursos gastados), tiempo promedio por objetivo, ritmo de expansion (edificios por minuto).
- Metrica que combina eventos: eficiencia de recursos, porque cruza RESOURCE_GATHERED con OBJECTIVE_COMPLETED.
- Regla de secuencia: RUSH_OPENING, tres UNIT_TRAINED militares antes del primer BUILDING_COMPLETED economico.
- Sirve como prueba de que un tercer dominio entra sin cambiar el nucleo: solo requiere un documento en `contracts/games/`, un analizador y un generador del simulador.
