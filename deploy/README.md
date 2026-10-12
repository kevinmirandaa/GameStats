# Despliegue con Docker

Todo el sistema se levanta con Docker Compose: tres servicios en una red interna, con un solo comando.

## Que es cada cosa

- **Imagen**: un paquete cerrado con un programa y todo lo que necesita para correr. Se construye una vez a partir de un `Dockerfile` y funciona igual en cualquier maquina.
- **Contenedor**: una imagen en ejecucion. Es como un programa corriendo aislado del resto de tu computador.
- **Dockerfile**: la receta para construir una imagen. Aqui cada modulo tiene el suyo, en dos etapas: la primera compila con todas las herramientas y la segunda copia solo el resultado, por eso las imagenes de Go pesan 21 MB.
- **Docker Compose**: un archivo (`docker-compose.yml`) que describe varios contenedores, como se conectan y en que orden arrancan, para levantarlos juntos.
- **Healthcheck**: una comprobacion que Docker repite para saber si un servicio ya responde. Compose espera a que Scala este sano antes de arrancar Go.
- **Volumen**: una carpeta que sobrevive cuando el contenedor se borra. Se usa para el registro de eventos rechazados.

## Servicios

| Servicio | Imagen | Puerto | Para que |
|---|---|---|---|
| analytics-scala | gamestats-analytics:local | 8081 | analisis y consulta de resultados |
| ingest-go | gamestats-ingest:local | 8080 | recibe y valida eventos, los envia a Scala |
| simulator | gamestats-simulator:local | no expone | genera partidas bajo demanda (perfil `simulation`) |

El simulador no arranca con `up`: se ejecuta cuando se pide.

## Requisitos

Docker Desktop abierto y en verde. Comprobar con:

```bash
docker info --format '{{.ServerVersion}}'
```

## Comandos

Desde la raiz del repositorio:

```bash
make up
```

Construye y levanta Scala y Go, y espera a que ambos esten sanos (unos 12 segundos con las imagenes ya construidas).

```bash
make simulate
```

Ejecuta el simulador con el escenario `mixed-parallel`. Para otro escenario o semilla:

```bash
SIMULATOR_SCENARIO=bulk SIMULATOR_RUN_ID=carga1 make simulate
```

Consultar resultados:

```bash
curl localhost:8081/stats/matches
curl localhost:8081/patterns
curl localhost:8080/metrics
```

Ver registros y detener:

```bash
make logs
make down
```

`make down` borra tambien el volumen, asi que el sistema vuelve a empezar vacio.

## Prueba de humo

```bash
make smoke
```

Levanta todo, ejecuta `mixed-parallel` y `unfinished-match`, y comprueba por HTTP:

- las 24 partidas de racing y combat analizadas y COMPLETED;
- los cuatro patrones detectados;
- agregados de juego y de jugador;
- eventos invalidos y duplicados rechazados, sin fallos de entrega;
- las partidas sin cierre llegando como INCOMPLETE.

Termina con codigo distinto de cero si algo falla. Al final apaga todo, salvo que se use `KEEP_UP=1 make smoke`.

## Variables

Se pueden fijar en el entorno o en un archivo `deploy/.env` (ver `deploy/.env.example`). El resto de variables de cada servicio esta en el README de su modulo.

## Apagado ordenado

`ingest-go` recibe la senal de parada y envia a Scala las partidas que seguian abiertas como INCOMPLETE antes de terminar. Por eso su `stop_grace_period` es de 50 segundos.

## Limites conocidos

- Si Scala no responde, Go reintenta el envio de cada partida con espera creciente. En Compose la ventana es de unos 15 segundos (6 intentos desde 500 ms). Si Scala sigue caido despues, esa partida se pierde y se cuenta en `matchesFailed` de `/metrics`. No hay cola de reenvio, y reenviar esa partida despues da 409 porque sus identificadores de evento y la partida ya quedaron registrados.
- Scala guarda los resultados en memoria: al reiniciarse el contenedor se pierde el historial.
- Los puertos 8080 y 8081 se publican en todas las interfaces de la maquina.
- `make logs` no termina: se detiene con Ctrl+C.
- Cada partida abierta reserva en Go un buzon de 1024 eventos (unos 180 KB). Cada servicio tiene un limite de memoria de 1 GB en Compose.
