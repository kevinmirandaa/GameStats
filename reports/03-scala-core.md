# Reporte fase 03 Scala nucleo y API

## Resultado

Modulo `analytics-scala` con el nucleo generico completo: dominio inmutable, decodificador con errores explicitos, analisis generico, registro de analizadores por juego, agregados de jugador y de juego, almacenamiento en memoria detras de un trait y API con todos los endpoints del Blueprint. No hay ninguna referencia a racing ni combat en el codigo de produccion.

## Verificado ejecutando

- `sbt test`: 37 pruebas en verde.
- El analisis generico de las 4 partidas de ejemplo coincide con los `*.expected.json` en total de eventos, duracion, conteos por tipo y por accion, y jugadores con su cantidad de eventos.
- Un juego desconocido produce analisis generico sin error.
- La API se probo con HTTP real sobre un servidor levantado en la prueba.
- Extremo a extremo con los binarios reales: Go acepto 79 eventos de dos partidas, las envio a Scala y los resultados coinciden (combat 99.4 s con 21, 15 y 11 eventos por jugador; racing 383.3 s con 30 eventos).
- Sin `var`, `null`, `throw` ni comentarios en el codigo de produccion.

## No verificado

- El Dockerfile no se construyo porque Docker Desktop estaba cerrado. Se prueba en la fase 06.

## Diseno

```
BatchDecoder -> MatchAnalysisPipeline -> AnalysisStore -> consultas
                      |
                GameAnalyzerRegistry -> GameAnalyzer del juego (opcional)
```

El pipeline ordena los eventos, ejecuta el analizador del juego si existe y arma el resultado con la lista de jugadores, los conteos y los patrones ordenados. Los agregados se calculan sobre los analisis guardados con `groupMap` y `sum`, `min`, `max`.

## Como agregar un analizador

Ver `analytics-scala/README.md`. Un archivo en `analysis/games/` y una linea de registro en `Main`.

## Decisiones nuevas

D20 a D22 en `docs/DECISIONS.md`. El campo opcional `winnerPlayerId` se agrego al MatchAnalysis y al Blueprint.

## Para la fase 04

- Los analizadores reciben el `MatchBatch` con los eventos ya ordenados por timestamp.
- `EventData` ofrece `number`, `text`, `boolean` y `texts` para leer `data` sin tocar ujson.
- Los resultados esperados completos estan en `contracts/examples/games/*/*.expected.json`.
- Pendiente de la fase 01: resultados esperados de los agregados de jugador y de juego. Las pruebas de agregados de esta fase usan datos sinteticos. En la fase 04 se verifican con las partidas reales.
