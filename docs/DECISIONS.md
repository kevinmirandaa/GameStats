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

## D16 Un actor por partida con cierre sin carreras

Decision: cada partida activa tiene una goroutine con un channel de entrada. El registro cierra la partida retirandola del mapa bajo un mutex y luego drena el channel. Despues del cierre los eventos de esa partida se rechazan con 409.

Razon: los envios al channel ocurren bajo el mismo mutex que el retiro, asi no se pierden eventos ni hay envios a una goroutine terminada. El envio nunca bloquea: si el channel esta lleno se responde 503.

## D17 Espera corta despues de MATCH_FINISHED

Decision: tras MATCH_FINISHED la partida espera `MATCH_FINISH_GRACE_MILLISECONDS` (500 por defecto) antes de cerrarse.

Razon: los eventos pueden llegar desordenados y MATCH_FINISHED puede llegar antes que otros. La espera permite incluirlos sin implementar logica de reordenamiento compleja.

## D18 Deduplicacion global en memoria

Decision: los eventId ya vistos se guardan en un conjunto en memoria durante toda la vida del proceso.

Razon: es lo mas simple que cumple el requisito. Con el volumen del proyecto la memoria es despreciable. Si el volumen creciera se cambia por una ventana de tiempo sin tocar al resto.

## D19 Reintentos hacia Scala

Decision: hasta 5 intentos con espera creciente (200 ms, 400 ms, 800 ms...). Las respuestas 4xx excepto 429 no se reintentan. Si todo falla se registra y se cuenta en `matchesFailed`.

Razon: un 4xx indica datos que Scala no aceptara nunca, reintentar solo desperdicia tiempo.

## D20 winnerPlayerId en el resultado de la partida

Decision: MatchAnalysis incluye `winnerPlayerId` cuando MATCH_FINISHED lo trae, y se omite si no.

Razon: los agregados genericos (victorias por jugador) necesitan saber quien gano sin leer los eventos originales. Es un campo opcional que no rompe a los consumidores existentes.

## D21 Versiones y librerias de Scala

Decision: Scala 3.3.6 LTS, sbt 1.10.11, Java 21, Cask 0.10.2, upickle 4.1.0, munit 1.0.4, empaquetado con sbt-native-packager (`sbt stage`).

Razon: versiones estables y compatibles entre si. sbt 2 y Java 25 estaban instalados en la maquina pero no se usan para compilar.

## D22 Forma de los agregados

Decision: `GET /stats/players/{id}` y `GET /stats/games/{id}` devuelven `aggregates`, un mapa de nombre a numero, con `matchesPlayed`, `wins`, `matchesAnalyzed`, `matchesCompleted`, `mean_`, `min_`, `max_` y `patternCount_`.

Razon: un mapa generico permite que juegos nuevos tengan agregados sin cambiar el modelo ni el nucleo.

## D23 Simulador independiente que solo habla HTTP

Decision: el simulador es un modulo Go aparte que no importa codigo de `ingest-go`. Repite el modelo de evento en su propio paquete.

Razon: asi se comporta como un juego externo real y demuestra que el contrato es suficiente. La pequena duplicacion del modelo es el precio de la independencia.

## D24 Historias de partida garantizadas

Decision: cada partida generada sigue una historia (`Natural`, `Comeback`, `Streak`, `Quiet`) construida para disparar o no disparar las reglas de forma garantizada, en lugar de depender del azar.

Razon: las reglas deben poder demostrarse siempre. Las pruebas verifican las historias con calculos propios sobre 125 combinaciones de semilla y numero de jugadores.

## D25 Entrega separada del contenido

Decision: lo que ocurre con las partidas al enviarse (desorden, duplicados, eventos invalidos, partida sin cierre, desconexion) se aplica despues de generarlas, en el paquete `scenario`.

Razon: cualquier juego nuevo hereda esos casos sin escribir nada. El desorden solo intercambia vecinos para que MATCH_FINISHED no se adelante mas que la espera de cierre de Go (D17).

## D26 Jugadores de un conjunto fijo por juego

Decision: cada juego tiene un conjunto fijo de 12 identificadores de jugador y cada partida toma de 4 a 8 de ellos.

Razon: un mismo jugador aparece en varias partidas, lo que permite demostrar los agregados por jugador entre partidas.

## D27 Compose con perfil para el simulador

Decision: Compose define tres servicios, pero el simulador esta en el perfil `simulation` y solo corre cuando se pide (`make simulate`). Go espera a que Scala este sano y el simulador a que Go lo este.

Razon: el simulador es una fuente externa que termina, no un servicio permanente. El orden por healthcheck evita errores de arranque.

## D28 Prueba de humo como verificacion de aceptacion

Decision: `qa/smoke.sh` levanta el sistema con Docker, ejecuta dos escenarios y verifica por HTTP resultados concretos. Es lo que ejecutan `make smoke` y, mas adelante, el pipeline de CI.

Razon: demuestra el sistema completo funcionando de punta a punta en un entorno limpio, que es lo que pide el enunciado para la ejecucion integrada.

## D29 Ventana de reintento ampliada en Compose y limites aceptados

Decision: en Compose, Go reintenta el envio a Scala 6 veces desde 500 ms (unos 15 segundos en total), en lugar de los 5 intentos desde 200 ms por defecto. No se agrega cola de reenvio ni persistencia en Scala.

Razon: el QA mostro que con la ventana por defecto (unos 3 segundos) una caida breve de Scala perdia partidas. Una cola persistente y una base de datos exceden lo que pide el enunciado (D09). El limite queda documentado en `deploy/README.md`.

## D30 Buzon por partida de 1024 eventos y limite de memoria

Decision: el buzon de cada partida abierta en Go pasa de 8192 a 1024 eventos y cada servicio tiene un limite de 1 GB en Compose.

Razon: el QA integral midio que 2000 partidas abiertas a la vez usaban 2.88 GiB. Con 1024 usan unos 450 MB y no se pierde ningun evento en las pruebas de carga. Si un buzon se llena, el evento se rechaza con 503 sin quedar registrado como visto, asi que el cliente puede reenviarlo.

## D31 Despliegue automatico con imagenes publicadas en GHCR

Decision: el entorno es Docker Compose con las imagenes publicadas en GitHub Container Registry. El pipeline de CD publica las imagenes de cada commit de `main` y luego las despliega en un entorno limpio y ejecuta la prueba de humo. No hay un servidor permanente.

Razon: el enunciado pide una version funcional desplegada en un entorno definido por el equipo, no una nube concreta. Esta opcion no necesita cuentas, tarjeta ni secretos, se repite en cada cambio y se puede copiar tal cual a cualquier maquina con Docker. Una direccion publica permanente queda como paso opcional documentado.

## D32 CI separado de CD

Decision: `ci.yml` valida cada Pull Request (formato, analisis estatico, pruebas, estandares, imagenes y prueba de humo) y `cd.yml` solo publica y despliega al unir a `main`.

Razon: los cambios se verifican antes de unirse y solo lo ya verificado se publica. Los permisos de escritura de paquetes solo existen en el trabajo de publicacion.

## D33 Panel web de resultados servido por Scala

Decision: el modulo Scala sirve en `GET /` una pagina HTML unica, con JavaScript sin librerias, que consulta la API existente y muestra partidas, metricas, patrones y agregados.

Razon: el enunciado pide consultar o presentar los resultados. La API en JSON cumple "consultar", pero un panel visual hace la demostracion y la verificacion mucho mas faciles. No agrega logica ni dependencias: usa los mismos endpoints, y el panel y `curl` siempre muestran los mismos numeros.

## D34 Entrada por carpeta de archivos ademas de HTTP

Decision: `ingest-go` puede vigilar una carpeta (`INBOX_DIRECTORY`) donde cualquier fuente deja archivos `.jsonl` o `.json` con eventos. Los archivos pasan por el mismo servicio de ingesta que las peticiones HTTP: misma validacion, deduplicacion y agrupacion por partida.

Razon: el enunciado permite que los eventos lleguen "desde archivos, una API REST, un flujo de mensajes u otro mecanismo", y describe a Go como el modulo que lee, identifica y organiza los datos de fuentes que se actualizan constantemente. Aceptar ambos mecanismos cubre los dos escenarios sin duplicar logica.

## D35 Lectura de archivos que crecen y de archivos a medio escribir

Decision: un `.jsonl` se lee como un registro que crece (solo lineas completas, con un desplazamiento por archivo). Un `.json` se lee cuando su tamano y fecha no cambian entre dos revisiones y despues se mueve a `processed/`, o a `failed/` si no es valido.

Razon: una fuente real puede estar escribiendo el archivo mientras Go lo mira. Esperar a que sea estable evita leer eventos cortados, y mover los archivos evita procesarlos dos veces.

## D36 La carpeta de entrada es un volumen de Docker

Decision: por defecto `/inbox` es un volumen compartido entre `ingest-go` y el simulador. Los archivos se dejan con `make inbox-add` o `docker compose cp`. Un archivo adicional (`docker-compose.inbox-folder.yml`) permite usar una carpeta real del computador.

Razon: en macOS, Docker Desktop no puede montar carpetas como Descargas, Escritorio o Documentos sin un permiso especial del sistema, y la prueba lo confirmo con un error. Un volumen funciona igual en cualquier maquina, incluida la de un tercero sin configuracion previa.

## D37 Resultados guardados en archivos JSON

Decision: Scala guarda cada analisis como un archivo JSON en `RESULTS_DIRECTORY` (un volumen en Docker) y los recarga al arrancar. La escritura es atomica (archivo temporal y renombrado) y un archivo danado se ignora y se reporta sin impedir el arranque. Sin esa variable se usa el almacen en memoria.

Razon: el enunciado pide resultados "consultados o almacenados", y el profesor habla de historial y de que las estadisticas "tienen que escribirse en algun lado". El almacen ya estaba detras de una interfaz (D09), asi que no cambia el resto del codigo. Una base de datos sigue sin ser necesaria.

## D38 Instaladores que solo requieren Docker

Decision: `instalar.sh` e `instalar.bat` comprueban Docker, construyen y levantan el sistema y abren el panel. `detener.sh` y `detener.bat` lo apagan conservando los resultados, o borrandolos con `--borrar-datos`.

Razon: el profesor pidio una instalacion sencilla en una maquina que puede no tener nada instalado, sin abrir un proyecto en un editor. La compilacion ocurre dentro de Docker al instalar, asi que no hacen falta Go, Scala ni Java en la maquina.

## D39 Conteo de eventos aceptados por juego

Decision: `/metrics` de Go informa `eventsAcceptedByGame`.

Razon: el enunciado menciona clasificar los eventos "por partida o juego". Go agrupa por partida y ahora tambien lleva la cuenta por juego, sin interpretar el contenido.
