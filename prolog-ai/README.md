# prolog-ai

Servicio de inteligencia de Conecta 4 en SWI-Prolog 9.

## API

`POST /decide`

```json
{"board": [[0,0,0,0,0,0,0], ... 6 filas de arriba hacia abajo], "player": 2, "depth": 5}
```

Celdas: 0 vacia, 1 rojo, 2 amarillo. Respuesta:

```json
{"column": 3, "score": -2, "nodes": 2806, "depth": 5, "candidates": [{"column": 3, "score": -2}, ...]}
```

`GET /health` responde `{"status":"UP"}`.

## Algoritmo

Negamax con poda alfa-beta. Las columnas se exploran del centro hacia afuera. La evaluacion suma ventanas de cuatro casillas (tres propias y una libre valen +50, dos y dos +10, y lo mismo negativo para el rival) y un peso por columna. Una victoria vale 1 000 000 mas la profundidad restante, para preferir ganar antes y perder despues.

## Pruebas

```bash
swipl -g run_tests -t halt tests/connect4_tests.pl
```

o con Docker: `make test-prolog`.
