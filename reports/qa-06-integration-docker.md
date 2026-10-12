# QA fase 06 Integracion con Docker Compose

Veredicto: APROBADO

Se partio y se termino con `make down`. Los contenedores PostgresDB, DataBases y un volumen ajenos ya existian y no se tocaron.

## Criterios

| Criterio | Resultado | Evidencia |
|---|---|---|
| compose config valido | OK | `docker compose -f deploy/docker-compose.yml config` salio 0 sin avisos |
| `make build` | OK | Tres imagenes construidas (con cache) |
| `make up` deja servicios sanos | OK | 11.9 s; `docker compose ps` muestra ambos (healthy); /health de 8080 y 8081 responden 200 `{"status":"UP"}` |
| `make smoke` completo | OK | 15 verificaciones OK, dos ejecuciones seguidas, 24 s cada una |
| El smoke falla de verdad | OK | Copia con 13 partidas esperadas y espera de 6 s: 6 FALLO/TIEMPO AGOTADO, exit 1. Copia con escenario inexistente: exit 1 |
| Falsos positivos en smoke.sh | OK | Cada check compara contra el run_id propio y los `|| true` solo estan en la espera; la comprobacion posterior cuenta el fallo |
| Scala caido durante una partida | OK con matiz | Ver abajo |
| Cierre ordenado de ingest-go | OK | Con 12 eventos activos, `restart` registro "closing active matches" y entrego match-racing-qa3 como INCOMPLETE con 12 eventos; exit 0 |
| Puertos y variables del Blueprint seccion 6 | OK | 8080, 8081, PORT, ANALYTICS_URL, MATCH_IDLE_TIMEOUT_SECONDS, REJECTED_EVENTS_PATH presentes; extras: MATCH_FINISH_GRACE_MILLISECONDS |
| Sin root | OK | ingest uid 10001, analytics uid 10001, USER en los tres Dockerfile |
| `make down` limpia | OK | Sin contenedores, red ni volumen gamestats (docker ps -a, network ls, volume ls) |
| Repetir up y smoke | OK | Dos smoke consecutivos y up tras down funcionan |
| deploy/README.md | OK | Ejecutados: docker info, make up, make simulate, SIMULATOR_SCENARIO=bulk SIMULATOR_RUN_ID=carga1 make simulate, curls de stats, patterns y metrics, make logs, make down, make smoke. Todos funcionan |
| Estandares | OK con menores | Sin comentarios, emojis, separadores ni secretos en Makefile, compose, .env.example, smoke.sh, READMEs y Dockerfiles |

## Robustez (descripcion real)

Con analytics-scala detenido se envio patterns-triggered (30 eventos) a POST /events/batch: Go responde 200 con 30 aceptados y la entrega ocurre en segundo plano tras 500 ms de gracia. Los reintentos son 5 con 200, 400, 800 y 1600 ms: ventana de unos 3 s. No se registra cada reintento; solo el resultado final.

- Primer intento: Scala se reinicio unos 18 s despues. La partida se perdio: un solo error "was not delivered ... no such host", matchesFailed=1, sin cola ni persistencia.
- Segundo intento: Scala arranco 1 s despues del envio. La partida llego COMPLETED con 30 eventos (matchesSent=1).

Conclusion: las caidas de Scala de menos de unos 3 s se absorben; mas largas pierden la partida. Es lo que dice el Blueprint, pero conviene documentarlo. Ademas Scala guarda en memoria: al reiniciarse pierde el historial.

## Bloqueantes

Ninguno.

## Menores

1. devops: ingest-go no registra los reintentos individuales; solo el fallo final. Dificulta observar el comportamiento.
2. devops: una partida que agota los reintentos se pierde sin cola de reenvio. Documentarlo en deploy/README.md o evaluarlo en la fase 07.
3. devops: qa/smoke.sh tiene lineas de mas de 140 caracteres (82-115) y numeros repetidos (24, 4, 12) fuera de constantes.
4. devops: los puertos se publican en 0.0.0.0; para uso local bastaria 127.0.0.1.
5. devops: README no menciona que `make logs` no termina (seguir con Ctrl+C) ni que la ventana de reintento es corta.
6. Verificado solo lo pedido: `make test` no se ejecuto en esta revision.
