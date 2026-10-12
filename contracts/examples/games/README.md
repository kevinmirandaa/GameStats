# Partidas de ejemplo por juego

Cada archivo `*.batch.json` es una partida completa en el formato MatchBatch que Go envia a Scala (`status` COMPLETED, eventos ordenados y sin duplicados). Cada evento cumple `contracts/event.schema.json`.

Cada `*.expected.json` es el MatchAnalysis esperado para esa partida. Las pruebas de Scala deben cargar el batch, analizarlo y comparar con tolerancia de 0.001.

Para pruebas de Go y del simulador, los eventos individuales estan en el arreglo `events` de cada batch.

## Partidas

| Juego | Archivo | Proposito |
|---|---|---|
| racing | patterns-triggered | match-racing-001: dispara RACE_COMEBACK e IMPROVING_LAP_STREAK (dos pilotos) |
| racing | patterns-not-triggered | match-racing-002: ninguna regla, con casi disparos |
| combat | patterns-triggered | match-combat-001: dispara KILL_STREAK (dos jugadores) y SCORE_COMEBACK |
| combat | patterns-not-triggered | match-combat-002: ninguna regla, con casi disparos |

## Casi disparos incluidos en las partidas sin patrones

- Racing: un piloto ultimo en todas las vueltas que no gana, rachas de solo dos vueltas, dos tiempos iguales que no cuentan como mejora, y un ganador que lidera toda la carrera, por lo que no hay remontada.
- Combat: una racha de dos eliminaciones, una desventaja de dos eliminaciones del ganador (el umbral es tres).
