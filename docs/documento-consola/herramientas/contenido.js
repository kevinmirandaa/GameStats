const cifras = {
  pruebasGo: 118,
  pruebasScala: 110,
  pruebasJava: 142,
  pruebasProlog: 18,
  pruebasHumo: 20,
  pruebasIntegracion: 69,
  pruebasExtremoAExtremo: 75
};

const totalPruebas = cifras.pruebasGo + cifras.pruebasScala + cifras.pruebasJava + cifras.pruebasProlog;

const portada = {
  institucion: "Instituto Tecnológico de Costa Rica",
  escuela: ["Escuela de Ingeniería en Computación", "Lenguajes de Programación"],
  proyecto: ["Proyecto 2: GameConsole", "Consola web de videojuegos orientada a objetos con inteligencia de juego en Prolog"],
  estudiantes: ["Kevin Miranda Méndez", "Carné 2024120764", "Victor Ubilla", "Carné (completar)"],
  profesor: "Oscar Víquez Acuña",
  semestre: "II Semestre 2026"
};

const bloques = [
  { t: "h1", text: "1. Descripción general" },
  { t: "p", text: "GameConsole es una consola web que permite jugar tres videojuegos, ejecutar varias partidas en paralelo y ver las estadísticas de esas partidas en un panel. Los juegos están programados en Java con orientación a objetos. Uno de ellos, Conecta 4, se juega contra la computadora, y cada jugada de la computadora la calcula un programa en Prolog." },
  { t: "p", text: "Mientras se juega, cada juego genera eventos con el formato de GameStats (el sistema del Proyecto 1) y la consola los envía a su módulo en Go. GameStats los analiza con su módulo en Scala. El panel de la consola solo consulta esos resultados y los muestra; no calcula ninguna estadística por su cuenta." },
  { t: "img", file: "diagrama-consola.png", widthPt: 430, caption: "Figura 1. Componentes de la solución y su comunicación." },
  { t: "table", widths: [22, 20, 58], head: ["Componente", "Tecnología", "Responsabilidad"], rows: [
    ["console", "Java 21, Javalin", "Consola web, carga de juegos, sesiones, envío de eventos y consulta de estadísticas"],
    ["Juegos", "Java 21 (JAR)", "Reglas y estado de Conecta 4, Memoria y Buscaminas"],
    ["prolog-ai", "SWI-Prolog 9", "Decide la jugada de la computadora en Conecta 4"],
    ["ingest-go", "Go", "GameStats: recibe, valida y agrupa los eventos por partida"],
    ["analytics-scala", "Scala 3", "GameStats: calcula estadísticas y patrones, y las guarda en archivos JSON"],
    ["demo", "Python", "Juega partidas de demostración a través de la consola"]
  ] },

  { t: "h1", text: "2. Instalación desde cero" },
  { t: "p", text: "Sirve para una computadora con Windows 10 u 11, macOS o Linux sin ninguna herramienta de desarrollo. La única herramienta necesaria es Docker Desktop; no hace falta instalar Java, Go, Scala, Prolog ni make, porque todo se compila dentro de contenedores. Se necesita conexión a internet y unos 6 GB de disco libres." },
  { t: "ol", items: [
    "Instalar Docker Desktop desde https://www.docker.com/products/docker-desktop con las opciones por defecto. En Windows, si ofrece activar WSL 2, aceptar y reiniciar. En Linux se instala Docker Engine con el plugin Compose.",
    "Abrir Docker Desktop y esperar a que indique que está en ejecución.",
    "Descomprimir GameStats_Codigo.zip (por ejemplo en el Escritorio). Queda una carpeta GameStats.",
    "Ejecutar el instalador. En Windows, doble clic en instalar.bat. En macOS y Linux, desde la Terminal:"
  ] },
  { t: "code", lines: ["cd ~/Desktop/GameStats", "bash instalar.sh"] },
  { t: "p", text: "La primera vez tarda entre 5 y 10 minutos porque descarga las imágenes base y compila los módulos. El instalador levanta todo, espera a que esté sano, juega diez partidas de demostración a través de la consola y abre el navegador. Si no se abre solo, usar estas direcciones o el archivo LEEME_PRIMERO.html:" },
  { t: "table", widths: [38, 62], head: ["Qué", "Dirección"], rows: [
    ["Consola de juegos", "http://localhost:8100"],
    ["Panel de estadísticas", "http://localhost:8100/#panel"],
    ["Panel propio de GameStats", "http://localhost:8081"]
  ] },
  { t: "p", text: "Para detener el sistema: bash detener.sh (en Windows, detener.bat). Los resultados se conservan para la próxima vez; para borrarlos, bash detener.sh --borrar-datos. Si algo falla, comprobar que Docker Desktop esté abierto y que los puertos 8080, 8081, 8090 y 8100 estén libres." },

  { t: "h1", text: "3. Ejecución y uso" },
  { t: "ul", items: [
    "En la consola, cada juego del catálogo tiene un botón Nueva partida. Se elige el modo, los nombres y las opciones (dificultad, tamaño).",
    "Cada partida abierta aparece como una pestaña dentro de la consola. Se pueden tener varias a la vez y cambiar entre ellas.",
    "También se pueden abrir otras pestañas del navegador y jugar partidas distintas al mismo tiempo. La lista Partidas en el servidor permite abrir en una pestaña una partida iniciada en otra.",
    "Conecta 4: se pulsa la columna donde caerá la ficha. Contra la computadora, Prolog responde enseguida. Memoria: se voltean dos cartas por turno. Buscaminas: clic descubre, clic derecho o el modo bandera marca una mina.",
    "El panel de estadísticas se actualiza solo cada dos segundos, aunque haya partidas en juego."
  ] },
  { t: "p", text: "Comandos útiles (con make, opcional):" },
  { t: "code", lines: ["make up       # construye y levanta todo", "make demo     # juega partidas de demostración", "make e2e      # prueba de extremo a extremo", "make test     # pruebas de todos los módulos", "make down     # detiene y borra los datos"] },

  { t: "h1", text: "4. Los tres juegos y la orientación a objetos" },
  { t: "table", widths: [17, 25, 58], head: ["Juego", "Modos", "Clases principales"], rows: [
    ["Conecta 4", "Contra la computadora (Prolog) o dos jugadores", "Board (tablero 6 por 7, detecta líneas de cuatro), Disc, ConnectFourPlayer con HumanPlayer y ComputerPlayer, ConnectFourMatch (turnos y resultado), ColumnAdvisor con PrologColumnAdvisor"],
    ["Memoria", "Dos jugadores o un jugador", "Card, Deck (mazo mezclado), MemoryPlayer, MemoryMatch (turnos, parejas y fin de partida)"],
    ["Buscaminas", "Un jugador, tres dificultades", "Cell, MineField (minas, números, cascada y primera casilla segura), RevealResult"]
  ] },
  { t: "ul", items: [
    "Encapsulación: el estado de cada juego es privado y solo cambia con métodos que validan las reglas (por ejemplo Board.drop o MineField.reveal).",
    "Abstracción y polimorfismo: ConnectFourPlayer es una clase abstracta con dos subclases; ColumnAdvisor es una interfaz que permite cambiar quién aconseja la jugada; GameSession y GameModule abstraen cualquier juego para la consola.",
    "Patrón Adapter: cada juego tiene su dominio propio y un adaptador (Connect4Adapter, MemoryAdapter, MinesweeperAdapter) que lo convierte a la interfaz común GameSession. Así la consola carga cualquier juego sin conocer sus clases.",
    "Patrón Observer: GameSession es el sujeto y anuncia lo que ocurre (inicio, jugadas, fin). La consola registra observadores: uno traduce los hechos a eventos de GameStats y otro lleva el registro de actividad reciente."
  ] },
  { t: "p", text: "Los juegos son JAR independientes que se copian a la carpeta de plugins. La consola los descubre al arrancar (URLClassLoader y ServiceLoader), de modo que agregar un cuarto juego no requiere modificar ni recompilar la consola. Hay una prueba automática que compila un juego nuevo en el momento y comprueba que se carga." },

  { t: "h1", text: "5. Inteligencia en Prolog" },
  { t: "p", text: "La computadora de Conecta 4 no elige al azar. La consola envía el tablero a un servicio SWI-Prolog (prolog-ai) y usa la columna que este responde. El servicio recibe una petición HTTP y devuelve la jugada:" },
  { t: "code", lines: ["POST /decide  {\"board\": [6 filas de 7 celdas, 0 vacía, 1 rojo, 2 amarillo],", "               \"player\": 2, \"depth\": 5}", "respuesta:    {\"column\": 3, \"score\": -2, \"nodes\": 2806,", "               \"candidates\": [{\"column\": 3, \"score\": -2}, ...]}"] },
  { t: "p", text: "Algoritmo (archivo prolog-ai/connect4.pl): negamax con poda alfa-beta. El tablero se representa como un término plano de 42 celdas. Las columnas se exploran del centro hacia afuera para podar más. En las hojas se evalúan las 69 ventanas de cuatro casillas posibles (tres propias y una libre suman 50, dos y dos suman 10, y valores negativos equivalentes para el rival) más un peso por columna que favorece el centro. Un tablero ganado vale 1 000 000 más la profundidad restante, de modo que la computadora prefiere ganar cuanto antes y perder lo más tarde posible." },
  { t: "p", text: "La dificultad es la profundidad de búsqueda: fácil 3, medio 5 y difícil 7. Se usan profundidades impares porque con las pares el algoritmo ve una jugada del rival de más y evalúa mal. Cada decisión queda registrada como un evento AI_DECISION que incluye el tablero, la columna elegida, la puntuación, los nodos buscados, la profundidad y la puntuación de cada columna, lo que permite examinar las decisiones en GameStats." },
  { t: "p", text: "Pruebas: 18 pruebas plunit comprueban que Prolog toma la victoria inmediata, bloquea la victoria del rival, abre por el centro, nunca elige una columna llena y rechaza tableros inválidos. La prueba de extremo a extremo juega cinco partidas intentando ganar con tres fichas en columna y verifica que la computadora la bloquea todas las veces." },

  { t: "h1", text: "6. Eventos compatibles con GameStats" },
  { t: "p", text: "Los juegos usan el contrato 1.0 de GameStats sin cambios: schemaVersion, eventId, timestamp, gameId, gameVersion, matchId, playerId, type, action y data. Los cinco tipos del contrato (MATCH_STARTED, PLAYER_JOINED, GAME_ACTION, PLAYER_LEFT, MATCH_FINISHED) se respetan; los hechos propios de cada juego son valores del campo action dentro de GAME_ACTION." },
  { t: "table", widths: [17, 83], head: ["Juego", "Eventos (action) además del inicio, ingreso, salida y fin de la partida"], rows: [
    ["Conecta 4", "MOVE, AI_DECISION, PLAYER_WON, PLAYER_LOST, PLAYER_DRAW"],
    ["Memoria", "FLIP_CARD, PAIR_FOUND, PAIR_MISSED, PLAYER_WON, PLAYER_LOST, PLAYER_DRAW"],
    ["Buscaminas", "REVEAL_CELL, FLAG_CELL, PLAYER_WON, PLAYER_LOST"]
  ] },
  { t: "p", text: "Los campos de cada evento están en contracts/games/connect4.md, memory.md y minesweeper.md. Identificación de las instancias: el matchId es el juego más 12 caracteres hexadecimales aleatorios (por ejemplo connect4-20782dab25bf), único por partida. El playerId es player- más el nombre del jugador sin acentos, y la computadora es siempre ai-prolog-connect4. Los eventos de inicio y fin de cada partida permiten a GameStats contar las instancias de cada juego. Si el jugador abandona la partida con el botón, o la partida queda 20 minutos sin actividad, la consola envía la salida de los jugadores y el fin de partida con resultado ABANDONED." },
  { t: "h2", text: "Envío concurrente y fallos" },
  { t: "ul", items: [
    "Cada partida tiene su propia cola ordenada. Un hilo virtual por partida la vacía enviando POST /events a ingest-go. Así una partida lenta no bloquea a las demás ni a la interfaz, y los eventos de una partida mantienen su orden.",
    "Los marcadores de tiempo son estrictamente crecientes dentro de una partida (microsegundos), por lo que Go los ordena sin ambigüedad.",
    "Si GameStats responde 5xx o no responde, el evento se reintenta con espera creciente (hasta 14 intentos, de 0,5 a 30 segundos, unos cuatro minutos en total). Los errores 4xx no se reintentan porque indican un evento que GameStats nunca aceptará.",
    "El juego sigue funcionando aunque GameStats esté caído. La consola muestra en la parte superior cuántos eventos se enviaron, cuántos esperan y cuántos fallaron. Al apagarse, abandona las partidas abiertas y espera a vaciar las colas."
  ] },
  { t: "p", text: "Comprobado: 18 partidas de los tres juegos jugadas en paralelo producen en GameStats 18 análisis, cada uno con su propio inicio, fin y jugadores, sin mezclar eventos. También se detiene ingest-go, se juega una partida completa y, al volver a levantarlo, la partida llega completa (15 eventos) sin pérdida." },

  { t: "h1", text: "7. Panel de estadísticas" },
  { t: "p", text: "El panel está en la segunda pestaña de la consola y se actualiza cada dos segundos. Separa los resultados generales de los resultados por juego:" },
  { t: "ul", items: [
    "Resultados generales: instancias ejecutadas (finalizadas y en curso), instancias en curso, finalizadas, jugadores distintos y eventos recibidos.",
    "Instancias por juego: una barra por juego con las partidas finalizadas y las que están en curso.",
    "Resultados por juego: instancias, en curso, eventos y las estadísticas de GameStats para ese juego (promedios, porcentajes de victorias, decisiones de Prolog, precisión, casillas descubiertas, entre otras). Los mínimos y máximos están plegados.",
    "Partidas analizadas, con filtro por juego."
  ] },
  { t: "p", text: "La consola no calcula estas cifras: su servidor reenvía sin modificar las respuestas de GameStats. Para exponerlas se extendió GameStats con dos cambios pequeños: ingest-go informa en /metrics las partidas abiertas por juego (activeMatchesByGame), y analytics-scala ofrece GET /stats/overview con las instancias, estados y jugadores por juego, y tiene un analizador por cada juego nuevo (Connect4Analyzer, MemoryAnalyzer, MinesweeperAnalyzer). Las instancias en curso solo las conoce Go y las finalizadas solo Scala, por eso el panel combina ambas respuestas." },

  { t: "h1", text: "8. Decisiones técnicas esenciales" },
  { t: "table", widths: [30, 70], head: ["Decisión", "Razón"], rows: [
    ["Java con juegos como plugins JAR", "El profesor recomendó diseñar los juegos como aplicaciones individuales y una consola con carga dinámica. Java es además el lenguaje orientado a objetos natural del curso."],
    ["Adapter y Observer", "Son los patrones pedidos por el profesor: Adapter para cargar juegos de forma uniforme y Observer para el manejo de eventos."],
    ["Prolog como servicio HTTP", "Se despliega igual en cualquier máquina con Docker, no exige instalar Prolog y la consola solo necesita saber una URL."],
    ["Minimax con poda alfa-beta", "Es un algoritmo clásico de juegos por turnos que Prolog expresa con claridad y que se puede explicar y verificar con pruebas."],
    ["Eventos en GAME_ACTION", "El enunciado prohíbe reemplazar el contrato. La acción es abierta, así que no hubo que modificar el contrato ni el módulo Go."],
    ["Cola por partida con hilos virtuales", "Aísla las partidas entre sí, conserva el orden dentro de cada una y soporta miles de partidas sin hilos de sistema."],
    ["El panel solo consulta", "Evita duplicar el cálculo de GameStats. La consola es un proxy de lectura."],
    ["Inactividad: consola 20 min, Go 30 min", "Una persona puede tardar minutos entre jugadas. La consola abandona primero la partida inactiva, así GameStats siempre recibe su fin y no la rechaza como partida ya cerrada."],
    ["Candado por partida y límite de consultas a Prolog", "Una prueba de carga mostró que bloquear con synchronized una llamada HTTP congelaba la consola. Con candados normales y como máximo cuatro consultas simultáneas a Prolog, la consola responde en menos de 0,1 s aun con 30 partidas difíciles a la vez."]
  ] },

  { t: "h1", text: "9. Docker, CI/CD y deployment" },
  { t: "p", text: "Cada componente tiene su Dockerfile con construcción en dos etapas. Las pruebas se ejecutan dentro de la construcción: la imagen de Java corre Maven con todas sus pruebas y la de Prolog corre plunit, así que una imagen con pruebas fallidas no se construye. El archivo deploy/docker-compose.yml integra seis servicios: analytics-scala, ingest-go, prolog-ai, console, y los perfiles opcionales demo y simulation. Las comprobaciones de salud ordenan el arranque: la consola espera a que GameStats y Prolog estén sanos." },
  { t: "table", widths: [30, 70], head: ["Pipeline", "Qué hace"], rows: [
    ["CI (cada Pull Request)", "Formato, análisis estático y pruebas con detector de carreras de Go; pruebas de Scala; Maven verify de la consola; pruebas plunit de Prolog; estándares de código; construcción de imágenes con prueba de humo de GameStats; y levantar todo, jugar la demostración y ejecutar la prueba de extremo a extremo de la consola."],
    ["CD (al unir a main)", "Publica las seis imágenes en GitHub Container Registry con la etiqueta del commit y latest, las descarga, levanta el sistema con deploy/docker-compose.published.yml, ejecuta la prueba de humo, juega la demostración y ejecuta la prueba de extremo a extremo contra el despliegue."]
  ] },
  { t: "p", text: "Entorno de deployment: Docker Compose con las imágenes publicadas. El mismo archivo levanta el sistema en cualquier máquina con Docker:" },
  { t: "code", lines: ["export GAMESTATS_REGISTRY=ghcr.io/kevinmirandaa", "export GAMESTATS_TAG=latest", "docker compose -f deploy/docker-compose.published.yml up -d --wait", "docker compose -f deploy/docker-compose.published.yml --profile demo run --rm demo"] },
  { t: "p", text: "La guía completa, incluido el regreso a una versión anterior, está en deploy/DEPLOY.md." },

  { t: "h1", text: "10. Pruebas y resultados" },
  { t: "table", widths: [38, 14, 48], head: ["Tipo", "Cantidad", "Qué verifica"], rows: [
    ["Unitarias de Java", String(cifras.pruebasJava), "Reglas de los tres juegos, patrones Adapter y Observer, carga dinámica de un JAR creado en la prueba, envío concurrente, reintentos, API REST y 20 partidas simultáneas"],
    ["Unitarias de Prolog", String(cifras.pruebasProlog), "Victoria inmediata, bloqueo, apertura, columnas llenas y tableros inválidos"],
    ["Unitarias de Go y Scala (GameStats)", String(cifras.pruebasGo + cifras.pruebasScala), "Validación, concurrencia por partida, carpeta de entrada, analizadores de los tres juegos nuevos y resumen de instancias"],
    ["Extremo a extremo de la consola", String(cifras.pruebasExtremoAExtremo), "Sistema completo en Docker: tres juegos jugados de principio a fin, Prolog bloqueando, estadísticas en vivo, 18 partidas en paralelo sin mezclar eventos, consola respondiendo con Prolog bajo carga, entradas inválidas y caída de GameStats"],
    ["Prueba de humo e integración de GameStats", cifras.pruebasHumo + " y " + cifras.pruebasIntegracion, "El sistema original sigue funcionando con la consola integrada"]
  ] },
  { t: "p", text: "En total, " + totalPruebas + " pruebas unitarias más las verificaciones de extremo a extremo. Una revisión independiente tipo QA recorrió el enunciado punto por punto y trató de romper el sistema con entradas inválidas, partidas concurrentes, caídas de Prolog y de GameStats y carga alta. No encontró fallos críticos; los hallazgos de robustez (bloqueo bajo carga de Prolog, cierre por inactividad, caídas largas de GameStats, JSON inválido y validación de enteros) se corrigieron y se volvieron a verificar." },

  { t: "h1", text: "11. Cumplimiento del enunciado" },
  { t: "table", widths: [56, 44], head: ["Requisito", "Dónde está"], rows: [
    ["Cargar y jugar tres videojuegos desde una consola web", "Secciones 3 y 4"],
    ["Partidas en paralelo, en una consola o en pestañas separadas", "Secciones 3 y 6"],
    ["Programación orientada a objetos en los juegos", "Sección 4"],
    ["Un juego contra la computadora con decisiones en Prolog", "Sección 5"],
    ["Eventos de inicio, desarrollo y fin, y eventos propios de cada juego", "Sección 6 y contracts/games"],
    ["Eventos compatibles con GameStats enviados mientras se juega, con envíos concurrentes", "Sección 6"],
    ["Identificación estable de cada instancia sin mezclar eventos", "Sección 6"],
    ["Panel con estadísticas por juego y generales obtenidas de GameStats", "Sección 7"],
    ["Cantidad de instancias de cada juego, actualizada en uso", "Sección 7"],
    ["Instalación, ejecución integrada y deployment reproducibles", "Secciones 2 y 9"],
    ["Repositorio Git, Docker, CI/CD y deployment", "Sección 9"],
    ["Documentación breve y video de la demostración", "Este documento y el Anexo"]
  ] },

  { t: "h1", text: "Anexo. Video de la demostración" },
  { t: "p", text: "Enlace al video (plataforma de acceso público): (pegar aquí el enlace)" },
  { t: "p", text: "Guion sugerido de unos 12 minutos:" },
  { t: "table", widths: [8, 42, 50], head: ["#", "Qué se muestra", "Qué se explica"], rows: [
    ["1", "Figura 1 y estructura del proyecto", "Consola en Java, juegos como plugins, Prolog, GameStats"],
    ["2", "bash instalar.sh en una máquina limpia (o el resultado)", "Solo se necesita Docker"],
    ["3", "Conecta 4 contra la computadora", "Cada jugada la decide Prolog; se muestra connect4.pl y una decisión AI_DECISION"],
    ["4", "Prolog bloqueando tres fichas en columna", "Búsqueda con minimax y poda alfa-beta, no azar"],
    ["5", "Memoria y Buscaminas", "Clases del juego, turnos, cascada y primera casilla segura"],
    ["6", "Varias partidas a la vez y dos pestañas del navegador", "Cada partida tiene su matchId; los eventos no se mezclan"],
    ["7", "Panel de estadísticas con partidas en curso", "Instancias por juego, generales y por juego, actualizadas en vivo desde GameStats"],
    ["8", "Código de Adapter, Observer y carga de plugins", "Cómo se agrega un juego sin tocar la consola"],
    ["9", "Pestaña Actions de GitHub y docker compose ps", "CI/CD, imágenes publicadas y deployment"]
  ] }
];

module.exports = { portada, bloques };
