# GameStats

Plataforma multilenguaje para recibir, validar y analizar eventos de partidas de videojuegos.

Proyecto del curso de Lenguajes de Programacion. Entrega: 18 de octubre de 2026.

## Idea central

La generacion de eventos esta separada de su analisis. Una fuente externa (hoy un simulador, mañana juegos reales) envia eventos por HTTP. Go los recibe, valida y organiza por partida. Scala los transforma de forma funcional en estadisticas y patrones.

```
Fuente de eventos (simulador o juego)
        |
        |  POST /events  (JSON)
        v
ingest-go  :8080     imperativo   ingesta, validacion, estado por partida, concurrencia
        |
        |  POST /analyze  (partida completa y ordenada)
        v
analytics-scala  :8081     funcional   metricas, patrones, consulta de resultados
        |
        |  GET /stats/...
        v
Consulta
```

## Modulos

- `ingest-go/` modulo Go de ingesta.
- `analytics-scala/` modulo Scala de analisis.
- `simulator/` fuente de eventos simulada.
- `contracts/` esquema del evento, ejemplos y contratos por juego.
- `deploy/` Docker Compose y despliegue.
- `qa/` pruebas de integracion y de humo.
- `docs/` documentacion.
- `agents/`, `prompts/`, `reports/` forma de trabajo con agentes de IA.
- `project/` enunciado original.

## Documentacion

- [Blueprint](docs/BLUEPRINT.md) arquitectura y contratos.
- [Decisiones](docs/DECISIONS.md) por que se eligio cada cosa.
- [Agregar un juego](docs/ADDING_A_GAME.md) como conectar un juego nuevo.
- [Guia de Git](docs/GIT_GUIDE.md) comandos usados en el proyecto.

## Estado

Fase 00 de 09 en curso. Ver `prompts/README.md` para el plan de fases.
