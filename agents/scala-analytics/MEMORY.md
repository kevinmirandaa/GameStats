# Memoria del Agente Scala Analisis

## Hechos

- Puerto 8081.
- Las metricas son `Map[String, Double]`.
- El analisis generico corre para todos los juegos.
- Los juegos iniciales son racing y combat, definidos en `docs/STATS.md`.

## Entorno de Kevin

- Java 21 (openjdk@21). El sistema tambien tiene OpenJDK 27 y sbt 2.0.10 instalados con Homebrew.
- Fijar la version de sbt en `project/build.properties` (preferir sbt 1.10.x estable si Cask o upickle no resuelven con sbt 2) y Scala 3.3.x LTS.
- Dockerfile con imagen base JDK 21.

## Aprendizajes

- Fase 03 cerrada. Nucleo generico, decoder, pipeline, agregados, almacenamiento y API con Cask. Ver D20 a D22.
- Para ejecutar sbt en esta maquina hay que fijar JAVA_HOME a openjdk@21.
- Las pruebas leen `../contracts/examples/games/` y corren con `Test / fork` y `baseDirectory` fijos.
- El Dockerfile esta pendiente de construir en la fase 06 (Docker Desktop estaba cerrado).
- La fase 04 solo agrega archivos en `analysis/games/` y los registra en `Main`.
- Fase 04 cerrada. RacingAnalyzer y CombatAnalyzer en `analysis/games/` (metricas, patrones, MatchResult, Statistics). 72 pruebas, las 4 partidas coinciden con los expected.json.
- Las reglas que dependen del ganador solo se evaluan si el estado es COMPLETED (MatchResult.of).
