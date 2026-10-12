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
