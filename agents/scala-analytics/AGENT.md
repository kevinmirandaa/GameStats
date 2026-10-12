# Agente Scala Analisis

## Rol

Implementar el modulo `analytics-scala`: decodificacion, analisis generico, analizadores por juego, patrones, almacenamiento en memoria y API de consulta.

## Carpetas que le pertenecen

`analytics-scala/`

## Contexto obligatorio

`agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md` (secciones 5, 6, 8 y 10), `agents/shared/CODE_STANDARDS.md`, `docs/STATS.md` cuando exista.

## Instrucciones

1. Scala 3, sbt, Cask, upickle, munit.
2. Paquetes sugeridos: `domain` (Event, MatchBatch, MatchAnalysis, Pattern), `analysis.generic`, `analysis.games` (un archivo por juego), `analysis.patterns`, `registry` (GameAnalyzerRegistry), `storage`, `http`.
3. Trait `GameAnalyzer` con `gameId` y una funcion pura que recibe la lista de eventos y devuelve metricas por jugador, metricas de partida y patrones.
4. El registro elige el analizador por gameId. Si no existe, solo corre el analisis generico.
5. Dominio inmutable, funciones puras, sin `var`, sin `null`, errores con `Either`.
6. Operaciones de coleccion justificadas: filter, map, flatMap, groupBy, foldLeft, reduce, collect, sliding.
7. Las reglas de secuencia se escriben con foldLeft o sliding sobre la lista ordenada.
8. El almacenamiento esta detras de un trait para poder cambiarlo despues.
9. Pruebas con munit para cada metrica y regla, incluyendo casos donde la regla no se dispara.
10. Dockerfile multi etapa.

## Entregable por fase

Codigo, pruebas pasando, Dockerfile, README corto del modulo y reporte en `reports/`.
