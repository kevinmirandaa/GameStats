# QA fase 01 Diseno de estadisticas

Veredicto: APROBADO

Sin hallazgos bloqueantes. Hay 5 hallazgos menores; el primero conviene corregirlo antes del merge porque es una regla del proyecto.

## Criterios de aceptacion

| Criterio | Resultado | Evidencia |
|---|---|---|
| Cada metrica es calculable solo con los eventos documentados | CUMPLE | Las metricas de los `.expected.json` coinciden con las documentadas en `docs/STATS.md` por juego (0 sin documentar). Todas las metricas documentadas estan cubiertas por al menos un ejemplo. Los campos de `data` usados en las partidas coinciden exactamente con los de `contracts/games/*.md` (ni sobran ni faltan). |
| Cada regla tiene caso positivo y negativo | CUMPLE | Las 4 reglas se disparan en `patterns-triggered` y no en `patterns-not-triggered`. Los casi-disparos documentados se comprobaron: combat tiene racha maxima 2 y desventaja maxima 2 (umbral 3); racing tiene rachas de a lo mas 2 vueltas y un ganador que nunca baja del primer puesto. |
| Resultados esperados verificados dos veces | CUMPLE | Recalculados por QA con codigo nuevo y por fuerza bruta (agrupacion por rachas, recorrido de marcador acumulado): patrones, desviacion estandar, adelantamientos y daño total coinciden. 0 diferencias. |
| Todos los eventos de ejemplo pasan el esquema | CUMPLE | 135 eventos validados con Ajv 2020-12 con formatos. IDs unicos, cabecera consistente, timestamps estrictamente crecientes, formato `evt-{matchId}-NNNN`. |
| Minimos del enunciado | CUMPLE | Estadistica por jugador, por partida, combinada y regla por secuencia, en ambos juegos (tabla de cumplimiento en `docs/STATS.md`). |

## Comprobaciones adicionales que paso

- Coherencia interna de racing: las posiciones de LAP_COMPLETED coinciden con el orden por tiempo acumulado en cada vuelta, sin empates, y el ranking final coincide con el orden por tiempo total.
- POSITION_CHANGED coincide con la posicion de la vuelta correspondiente y nunca tiene from igual a to.
- Combat: ningun evento menciona un jugador inexistente ni hay autoeliminaciones. El ranking coincide con los jugadores que se unieron y el ganador es el primero del ranking.
- Las tablas de `docs/STATS_EXPECTED_RESULTS.md` coinciden con los `.expected.json` celda por celda (metricas de partida y de cada jugador, incluidas las que deben faltar).
- Fase 00 intacta: el esquema no cambio y los 12 ejemplos validos y 17 invalidos siguen comportandose igual.
- Sin emojis ni lineas decorativas en `docs/`, `contracts/`, `reports/`, `prompts/` y `agents/`.

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

M1. Las decisiones tomadas en la fase 01 no estan en `docs/DECISIONS.md`. Faltan: agregados genericos entre partidas con prefijos `mean_`, `min_`, `max_`; funcion opcional `additionalPlayerIds` en `GameAnalyzer`; `details` de patrones solo numerico; metricas no definidas se omiten en lugar de rellenarse; umbrales de las reglas. La regla del proyecto exige registrarlas. Los prompts de las fases 02, 03 y 05 ya las recogen, pero el registro de decisiones no. Corregir agregando D11 a D14. Agente: architect.

M2. `docs/ADDING_A_GAME.md` no menciona que un juego puede declarar participantes que no emiten eventos propios (el caso de las victimas en combat). Agregar una linea en el nivel 2. Agente: architect.

M3. No hay resultados esperados para los agregados entre partidas (`/stats/players/{id}` y `/stats/games/{gameId}`). La fase 03 implementara esos endpoints y sus pruebas no tendran una referencia independiente. Conviene que stats-designer agregue un `aggregate.expected.json` por juego calculado sobre las dos partidas de ejemplo antes de la fase 03. Agente: stats-designer.

M4. PLAYER_LEFT esta documentado como evento pero ningun ejemplo de juego lo usa y `docs/STATS.md` no dice que efecto tiene sobre las metricas. Agregar una frase (por ejemplo: no altera metricas, solo cuenta en los conteos genericos) y un ejemplo cuando se haga el escenario `disconnect` del simulador. Agente: stats-designer.

M5. Los archivos `prompts/README.md`, `02-go-ingest.md`, `03-scala-core.md` y `05-simulator.md` cambiaron despues de la fase 01. Los cambios son coherentes con las decisiones de la fase 01 y con el hallazgo menor M2 de la fase 00 (tabla de fases corregida), por lo que se aceptan. Informativo: el cambio pertenece al arquitecto, no al stats-designer.

## Comandos ejecutados

- Validacion Ajv de los 29 ejemplos de la fase 00 y de los 135 eventos de la fase 01.
- Script QA propio (`qa01.py`, fuera del repositorio) con: coherencia estructural de las 4 partidas, cruce de campos `data` contra los contratos de juego, cruce de metricas del JSON contra `docs/STATS.md`, cruce celda por celda de las tablas de `STATS_EXPECTED_RESULTS.md` contra los JSON, y recalculo independiente por fuerza bruta de patrones y varias metricas.
- Escaneo de emojis y lineas decorativas con Python.
- Verificacion de existencia de archivos y de la memoria del agente.

Una primera pasada del script marco 4 fallas de autoeliminacion que eran un error de la propia comprobacion (comparaba dos valores ausentes). Se corrigio la comprobacion y se repitio completa: 0 fallas.

## Nota de independencia

La fase 01 y esta revision salieron de la misma sesion de planificacion, no de sesiones separadas. Las comprobaciones son codigo nuevo y distinto del usado por el autor, pero no sustituyen una revision humana de las decisiones de diseno. Kevin deberia leer `docs/STATS.md` y confirmar que las metricas y reglas le parecen razonables antes del merge, porque es lo que defendera ante el profesor.
