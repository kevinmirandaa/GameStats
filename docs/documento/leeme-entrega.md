# GameStats

Plataforma multilenguaje para recibir, validar y analizar eventos de partidas de videojuegos. Proyecto del curso de Lenguajes de Programación, Instituto Tecnológico de Costa Rica.

Un simulador (o cualquier juego) envía eventos por HTTP, o deja archivos JSON/JSONL en una carpeta que Go vigila. **Go** (imperativo) los recibe, valida y agrupa por partida con una goroutine por partida. **Scala** (funcional) los transforma en estadísticas y patrones y los expone en JSON. Los resultados se guardan como archivos JSON y se recargan al reiniciar.

```
Fuente de eventos (simulador o juego)
        |  POST /events   (JSON)
        v
ingest-go  :8080      ingesta, validación, estado por partida, concurrencia
        |  POST /analyze  (partida completa y ordenada)
        v
analytics-scala  :8081      métricas, patrones, consulta de resultados
        |  GET /stats/...   /patterns
        v
Consulta
```

## Estructura

| Carpeta | Contenido |
|---|---|
| `ingest-go/` | Módulo Go de ingesta |
| `analytics-scala/` | Módulo Scala de análisis |
| `simulator/` | Fuente de eventos simulada (Go) |
| `contracts/` | Esquema JSON del evento, ejemplos y contratos de cada juego |
| `deploy/` | Docker Compose y guía de despliegue |
| `qa/` | Pruebas de integración, de humo, de carga y de estándares |
| `.github/workflows/` | Pipelines de CI/CD |
| `docs/` | Decisiones de diseño, estadísticas y cómo agregar un juego |

La documentación completa del proyecto está en el documento PDF entregado junto al código.

## Ejecutar

Requisito único: Docker con Compose (Docker Desktop). Instalación y demostración en un solo paso:

```bash
./instalar.sh            # macOS y Linux
instalar.bat           # Windows
./detener.sh             # detener (detener.bat en Windows)
```

Panel web: http://localhost:8081. Alternativa con `make`:

```bash
make up          # construye y levanta Scala y Go
make simulate    # genera 24 partidas de racing y combat
make inbox-demo  # entrega una partida por la carpeta de entrada
# abrir http://localhost:8081 en el navegador: panel web con los resultados
curl localhost:8081/stats/matches
curl localhost:8081/patterns
make down
```

## Pruebas

```bash
make test        # Go (ingest-go y simulator) y Scala. Requiere Go 1.22+, Java 21 y sbt
make smoke       # prueba de humo de extremo a extremo con Docker
```

## Repositorio

https://github.com/kevinmirandaa/GameStats
