# Registro de decisiones

Cada decision tiene contexto, alternativas y razon. Las nuevas se agregan al final.

## D01 Comunicacion entre Go y Scala por REST con JSON

Contexto: el enunciado permite HTTP, archivos estructurados o mensajeria.

Alternativas: RabbitMQ, Kafka, archivos compartidos.

Decision: HTTP con JSON.

Razon: es la opcion mas simple de implementar, probar con curl y contenerizar. Los brokers aportan persistencia, reproceso y colas que el enunciado no exige, a costa de mas infraestructura y conceptos (exchanges, particiones, offsets, duplicados). La concurrencia exigida ocurre dentro de Go, no en el transporte.

## D02 Sobre de evento comun con datos especificos libres

Contexto: el sistema debe procesar juegos distintos sin cambiar el nucleo.

Decision: un sobre fijo (`schemaVersion`, `eventId`, `timestamp`, `gameId`, `gameVersion`, `matchId`, `playerId`, `type`, `action`) y un campo `data` de forma libre.

Razon: Go valida solo lo comun. Lo propio de cada juego viaja en `data` y lo interpreta el analizador de ese juego.

## D03 Tipo cerrado y accion abierta

Decision: `type` es uno de cinco valores (MATCH_STARTED, MATCH_FINISHED, PLAYER_JOINED, PLAYER_LEFT, GAME_ACTION). `action` es texto libre y solo se exige en GAME_ACTION.

Razon: el ciclo de vida de una partida es igual en todos los juegos, por eso Go puede cerrar partidas y detectar jugadores sin conocer ningun juego. Lo especifico de un juego va en `action`.

## D04 Un campo de version del contrato

Decision: `schemaVersion` obligatorio, hoy "1.0".

Razon: permite evolucionar el contrato sin romper a las fuentes ya conectadas.

## D05 Metricas como mapas de texto a numero

Decision: en Scala las metricas son `Map[String, Double]` y los patrones llevan un mapa `details`.

Razon: un juego nuevo agrega metricas sin cambiar el modelo ni la API.

## D06 Analizadores por juego registrados por gameId

Decision: un trait `GameAnalyzer` y un registro. El analisis generico corre siempre. Si no hay analizador para un juego, solo se entrega el analisis generico.

Razon: agregar un juego es agregar un archivo, sin tocar el nucleo.

## D07 Cierre de partida en Go

Decision: una partida se cierra con MATCH_FINISHED (estado COMPLETED) o por inactividad configurable (estado INCOMPLETE). Se ordena por timestamp y se descartan duplicados por eventId antes de enviar.

Razon: las fuentes reales envian eventos tarde, repetidos y fuera de orden, y las partidas pueden no terminar nunca.

## D08 Juegos iniciales

Decision: racing y combat.

Razon: producen estadisticas muy distintas (tiempos y posiciones contra eliminaciones y daño) y secuencias claras (remontada, racha), lo que demuestra la genericidad con dos dominios. Estrategia queda como tercer dominio opcional.

## D09 Sin base de datos externa

Decision: almacenamiento en memoria detras de una interfaz.

Razon: el enunciado no exige persistencia. La interfaz permite cambiarla despues sin tocar el resto.

## D10 Versiones de herramientas

Decision: Go segun el instalado (1.27), Scala 3.3 LTS, Java 21.

Razon: Java 21 es LTS estable para Scala 3.3. El Java mas reciente instalado en la maquina no se usa para compilar.

## D11 Agregados genericos entre partidas

Decision: los agregados de jugador y de juego se calculan en el nucleo de Scala con los prefijos `mean_`, `min_`, `max_` y `patternCount_` sobre las metricas del analisis, sin conocer el juego.

Razon: un juego nuevo obtiene sus agregados historicos sin cambiar el nucleo.

## D12 Participantes sin eventos propios

Decision: `GameAnalyzer` tiene una funcion opcional `additionalPlayerIds(events)` con valor por defecto vacio. Combat la usa para los jugadores que solo aparecen como `victimPlayerId`.

Razon: la lista de jugadores de una partida debe incluir a quien participa aunque no emita eventos, sin que el nucleo interprete `data`.

## D13 Detalles de patrones numericos

Decision: `details` de un patron es `Map[String, Double]`.

Razon: mantiene el modelo de resultado simple y uniforme para cualquier juego. Los identificadores van en `playerId`, `startEventId` y `endEventId`.

## D14 Metricas no definidas se omiten

Decision: una metrica sin datos suficientes no aparece en el resultado. No se rellena con cero ni con NaN. Los conteos y sumas valen 0.

Razon: un cero inventado se confunde con un valor real y distorsiona promedios y minimos de los agregados.

## D15 Criterio de simplicidad

Decision: ante varias opciones validas se elige la mas simple que cumpla el enunciado y funcione. Los umbrales de reglas de la fase 01 se mantienen.

Razon: el plazo es corto y el proyecto se evalua por cumplir los requisitos y por poder justificar cada decision.
