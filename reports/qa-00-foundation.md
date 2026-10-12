# QA fase 00 Fundacion

Veredicto: APROBADO

Sin hallazgos bloqueantes. Hay 4 hallazgos menores, ninguno impide el merge.

## Criterios de aceptacion

- Los ejemplos validos pasan el esquema y los invalidos fallan: CUMPLE. Se reejecuto la validacion con Ajv 8 (2020-12, formatos activados): 12 validos aceptados, 17 invalidos rechazados, 0 discrepancias.
- Ningun archivo contiene emojis ni lineas decorativas: CUMPLE. Busqueda de emojis sobre todos los `.md`, `.json` y `.gitignore` fuera de `project/`: 0 resultados. Busqueda de lineas compuestas solo por guiones, iguales, asteriscos o guiones bajos: 0 resultados en todo el repositorio.
- Los documentos son consistentes con el Blueprint: CUMPLE. La lista de cinco tipos de evento es identica en `docs/BLUEPRINT.md` y `docs/DECISIONS.md`. Los puertos 8080 y 8081 coinciden en el README raiz y en los README de modulo.

## Entregables del prompt

- Estructura de carpetas con README en cada modulo vacio: CUMPLE. Existen `ingest-go`, `analytics-scala`, `simulator`, `deploy`, `qa` y `reports`, cada uno con README.
- `.gitignore`: CUMPLE. Cubre Go, Scala/sbt, IDEs y sistema.
- `README.md` raiz: CUMPLE.
- `docs/DECISIONS.md`: CUMPLE. 10 decisiones con contexto, alternativas y razon.
- `docs/ADDING_A_GAME.md`: CUMPLE. Cubre juego externo en otro lenguaje.
- `docs/GIT_GUIDE.md`: CUMPLE PARCIALMENTE. Ver hallazgo M1.
- Ejemplos: CUMPLE. 12 validos (minimo 8) y 17 invalidos (minimo 8). Hay dos eventos de un juego ficticio `treasure-hunt`.
- Revision del esquema contra los ejemplos: CUMPLE. Sin cambios necesarios.

## Revision de codigo y estandares

No hay codigo de aplicacion en esta fase. Los JSON estan bien formados, sin comentarios, con nombres de archivo que describen el caso. No hay referencias a un juego concreto en el esquema.

## Propiedad de archivos

Todo lo creado esta dentro de lo que le pertenece al arquitecto (`contracts/`, `docs/`, raiz) mas los README de modulo declarados en el prompt. No se tocaron carpetas de otros agentes. No se uso `git diff` porque los comandos Git los ejecuta Kevin; la comprobacion se hizo por listado de archivos.

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

M1. `docs/GIT_GUIDE.md` no explica `git init` ni el comando `git branch`, y `merge` aparece solo como boton de GitHub. El prompt los pedia. Impacto bajo: `init` ya se hizo y las ramas se crean con `git switch -c`. Corregir en la fase 09 o antes, agregando una seccion corta para esos tres puntos. Agente: architect.

M2. `prompts/README.md` tiene la tabla de fases sin fila separadora, por lo que puede mostrarse mal en algunos visores. Archivo fuera de la fase 00. Agente: architect.

M3. `README.md` raiz dice "Fase 00 de 09 en curso". Quedara desactualizado tras el merge. Actualizar al cerrar cada fase o quitar la linea.

M4. Casos de rechazo no cubiertos por los ejemplos: `data` igual a `null`, fecha sin hora, `eventId` duplicado entre dos eventos (lo cubre la prueba de Go, no el esquema). Conviene agregarlos cuando se implemente la fase 02.

## Comandos ejecutados

- Validacion Ajv de los 29 ejemplos contra `contracts/event.schema.json`.
- Conteo de archivos en `valid/` y `invalid/`.
- Comprobacion de existencia de carpetas, README y documentos.
- Busqueda de emojis, lineas decorativas y comentarios en archivos de texto y JSON.
- Verificacion de JSON bien formado en esquema y ejemplos.
- Cruce de tipos de evento y puertos entre documentos.

## Nota de independencia

El trabajo de la fase 00 y esta revision se ejecutaron en la misma sesion de planificacion, no en sesiones separadas. Por eso la revision se apoyo en comandos ejecutados y no en una segunda lectura independiente. Para las fases de codigo conviene correr el QA en una sesion nueva.
