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

Agregar aqui lo que se descubra durante las fases.
