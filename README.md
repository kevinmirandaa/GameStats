# GameStats

Plataforma multilenguaje para recibir, validar y analizar eventos de partidas de videojuegos.

Proyecto del curso de Lenguajes de Programacion. Entrega: 18 de octubre de 2026.

## Idea central

La generacion de eventos esta separada de su analisis. Una fuente externa (hoy un simulador, mañana juegos reales) envia eventos por HTTP. Go los recibe (HTTP o carpeta vigilada), valida y organiza por partida. Scala los transforma de forma funcional en estadisticas y patrones.

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
- [Despliegue](deploy/DEPLOY.md) pipelines y como levantar el sistema.
- [Estadisticas](docs/STATS.md) metricas y reglas de cada juego.

## Estado

Proyecto completo: módulos Go y Scala, simulador, Docker Compose, CI/CD y despliegue. Ver `docs/documento/` para el documento entregable y `deploy/DEPLOY.md` para el despliegue.

## Ejecutar

Solo con Docker: `./instalar.sh` (macOS y Linux) o `instalar.bat` (Windows); abrir luego http://localhost:8081. Con make:

```bash
make up
make simulate
curl localhost:8081/stats/matches
make down
```

Los resultados también se ven en el navegador, en `http://localhost:8081` (panel web), después de `make simulate`.
