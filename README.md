# GameStats y GameConsole

Proyectos 1 y 2 del curso de Lenguajes de Programación, Instituto Tecnológico de Costa Rica.

- **GameStats** (Proyecto 1) recibe eventos de partidas, los valida y los agrupa con Go, y los convierte en estadísticas y patrones con Scala.
- **GameConsole** (Proyecto 2) es una consola web con tres videojuegos orientados a objetos en Java, una inteligencia en Prolog, y un panel que muestra las estadísticas que calcula GameStats.

```
Navegador
   |  http://localhost:8100
   v
console (Java)  ----- POST /decide ----->  prolog-ai (SWI-Prolog)
   |  juegos como plugins JAR
   |  POST /events (concurrente, por partida)
   v
ingest-go (Go)  :8080      validación, estado por partida, concurrencia
   |  POST /analyze
   v
analytics-scala (Scala)  :8081      métricas, patrones, estadísticas
   ^
   |  GET /stats/...   (el panel de la consola solo consulta)
console
```

## Ejecutar

Requisito único: Docker con Compose (Docker Desktop).

```bash
./instalar.sh            # macOS y Linux
instalar.bat             # Windows
./detener.sh             # detener (detener.bat en Windows)
```

El instalador construye todo, juega partidas de demostración y abre la consola.

| Qué | Dirección |
|---|---|
| Consola de juegos | http://localhost:8100 |
| Panel de estadísticas | http://localhost:8100/#panel |
| Panel propio de GameStats | http://localhost:8081 |

## Estructura

| Carpeta | Contenido |
|---|---|
| `console/` | GameConsole en Java: API común, tres juegos y servidor web |
| `prolog-ai/` | Servicio de inteligencia en SWI-Prolog |
| `demo/` | Cliente que juega partidas de demostración por la API de la consola |
| `ingest-go/` | Módulo Go de GameStats |
| `analytics-scala/` | Módulo Scala de GameStats |
| `simulator/` | Fuente de eventos simulada de GameStats |
| `contracts/` | Esquema del evento y contratos de cada juego |
| `deploy/` | Docker Compose |
| `qa/` | Pruebas de humo, integración, extremo a extremo y estándares |
| `.github/workflows/` | CI y CD |
| `docs/` | Decisiones, estadísticas y cómo agregar un juego |

## Pruebas

```bash
make test        # Go, Scala, Java (en Docker) y Prolog (en Docker)
make smoke       # GameStats de extremo a extremo
make e2e         # GameConsole de extremo a extremo (con el sistema levantado)
```

## Repositorio

https://github.com/kevinmirandaa/GameStats
