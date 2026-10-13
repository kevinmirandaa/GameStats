# qa

Pruebas que cruzan varios módulos. Las pruebas unitarias viven dentro de cada módulo.

| Archivo | Qué hace |
|---|---|
| `smoke.sh` | Prueba de humo: levanta el sistema con Docker Compose, ejecuta dos escenarios del simulador y verifica los resultados por HTTP, incluido el panel web (15 verificaciones). Se ejecuta con `make smoke`. |
| `integration.sh` | 68 verificaciones de extremo a extremo: eventos válidos, inválidos, duplicados y desordenados, partidas en paralelo, partidas sin cierre, caída de Scala y un juego inventado. |
| `load.sh` | Prueba de carga: unos 42 000 eventos, 2 000 partidas abiertas a la vez, tiempos y memoria. |
| `check-standards.sh` | Busca comentarios, `var`, `null`, errores ignorados y otras violaciones de los estándares de código. |
| `event_builder.py`, `standards_scanner.py` | Utilidades de los scripts anteriores. |

Requisitos: Docker con Compose, `curl`, `python3` y `make`.
