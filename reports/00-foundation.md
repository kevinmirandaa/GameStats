# Reporte fase 00 Fundacion

Agente: architect

## Creado

- `.gitignore` para Go, Scala/sbt, IDEs y sistema.
- `README.md` raiz.
- `README.md` en `ingest-go`, `analytics-scala`, `simulator`, `deploy`, `qa`, `reports`.
- `docs/DECISIONS.md` con 10 decisiones (D01 a D10).
- `docs/ADDING_A_GAME.md` con dos niveles: conectar un juego y agregar sus estadisticas.
- `docs/GIT_GUIDE.md` con comandos, mensajes de commit y ciclo de fase.
- `contracts/examples/valid/` con 12 eventos validos, incluido un juego ficticio `treasure-hunt` y un timestamp con zona horaria.
- `contracts/examples/invalid/` con 17 eventos invalidos, nombrados por el motivo de rechazo.
- `contracts/examples/README.md`.

## Verificacion

Se validaron los 29 ejemplos contra `contracts/event.schema.json` con Ajv 8 (borrador 2020-12, formatos activados). Resultado: los 12 validos pasan, los 17 invalidos fallan, 0 discrepancias. El esquema no necesito cambios.

## Decisiones nuevas

Ninguna que cambie el Blueprint. Se registro D10 (versiones de herramientas).

## Riesgos y notas para otras fases

1. `format: date-time` solo se comprueba si el validador activa formatos. Go debe validar con `time.Parse(time.RFC3339Nano, ...)` y normalizar a UTC.
2. El esquema no puede expresar "eventId unico": la deduplicacion es responsabilidad de Go.
3. `additionalProperties: false` rechaza campos extra en el sobre. Todo lo adicional debe ir en `data`. Los agentes de Go y del simulador deben respetarlo.
4. Un `playerId` vacio se rechaza (`minLength: 1`).
5. Con Go 1.27 instalado, el agente de Go debe fijar la directiva `go` del `go.mod` a la version instalada.
6. Las carpetas vacias no entran en Git. Cada una tiene un README para que existan en el repositorio.

## Fuera de lo pedido

No se ejecuto ningun comando Git. Se instalo Ajv unicamente en el directorio temporal de la sesion para validar, no en el proyecto.
