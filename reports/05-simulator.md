# Reporte fase 05 Simulador

## Resultado

Modulo `simulator` completo: generadores de racing y combat, diez escenarios, ejecucion en paralelo con goroutines, modo tiempo real simulado y volcado rapido, semilla reproducible. No importa codigo de `ingest-go`.

## Verificado ejecutando

- `go vet` y `gofmt` limpios, `go test -race -count=2 ./...` en verde.
- Cada partida generada cumple el contrato (revisado con un verificador de sobre que a su vez se prueba contra los 31 ejemplos de `contracts/examples`), tiene ids unicos, esta ordenada por timestamp, empieza con MATCH_STARTED y termina con MATCH_FINISHED.
- Las posiciones de racing son coherentes con los tiempos acumulados y el ranking sigue el tiempo total (el empate exacto se desempata por orden de parrilla).
- Historias garantizadas, con calculos propios de las reglas, sobre 5 tamanos de partida por 25 semillas: Comeback hace que el ganador vaya cuarto o peor, Streak da una racha de al menos tres vueltas o cuatro eliminaciones, Quiet no dispara ninguna regla.
- La misma semilla genera exactamente las mismas partidas, y las partidas se envian de forma concurrente.
- Extremo a extremo con los binarios reales (simulador, Go y Scala): 24 partidas del escenario `mixed-parallel`, 965 eventos enviados, 937 aceptados y 28 rechazados (21 duplicados y 7 invalidos, todos esperados), 0 fallos de entrega. Scala analizo las 24 partidas como COMPLETED y detecto RACE_COMEBACK 3, IMPROVING_LAP_STREAK 15, SCORE_COMEBACK 3 y KILL_STREAK 14.

## Volumen por escenario (partidas por juego por defecto)

| Escenario | Partidas totales | Eventos |
|---|---|---|
| normal | 8 | 418 |
| comeback | 8 | 265 |
| killstreak | 8 | 299 |
| out-of-order | 8 | 418 |
| duplicates | 8 | 504 |
| invalid-events | 8 | 457 |
| disconnect | 8 | 426 |
| unfinished-match | 8 | 332 |
| bulk | 400 | 20980 |
| mixed-parallel | 24 | 965 |

## No verificado

- El Dockerfile no se construyo porque Docker Desktop estaba cerrado. Se prueba en la fase 06.
- El escenario `unfinished-match` se probo en generacion y entrega, pero la partida INCOMPLETE completa depende del tiempo de inactividad de Go. Se comprobara en la prueba de humo.

## Decisiones nuevas

D23 a D26 en `docs/DECISIONS.md`.

## Para la fase 06

- Imagen y variables: `SIMULATOR_TARGET_URL=http://ingest-go:8080` por defecto en el contenedor.
- Para la prueba de humo: `-scenario mixed-parallel -run-id <valor>`. Para ver una partida INCOMPLETE hay que bajar `MATCH_IDLE_TIMEOUT_SECONDS` en Go.
- El simulador espera hasta 30 segundos a que Go responda `/health`.

## Despues del QA

El primer QA independiente (`reports/qa-05-simulator.md`) rechazo la fase por dos bloqueantes, ya corregidos:

- La semilla no fijaba los timestamps. Se agrego `-start-time` y `SIMULATOR_START_TIME`. Verificado con el binario real: dos ejecuciones con la misma semilla y hora de inicio producen exactamente el mismo contenido (965 eventos, mismo hash).
- La interfaz se llamaba `Generator`. Ahora es `GameMatchGenerator`, como pide el prompt.

Tambien se corrigieron varios menores: el jugador que se desconecta ya no actua despues de salir, se rechazan `NaN` y ritmos diminutos, los enteros de entorno se leen de forma estricta (`12abc` falla), se rechazan juegos repetidos e identificadores de corrida con caracteres raros, y `-h` termina con codigo 0.

Quedan aceptados como menores: numeros magicos de las constantes de generacion dentro de `racing.go` y `combat.go`, y que un reintento tras un 5xx pueda contarse como rechazado si el primer intento si llego.
