# Memoria del Agente QA

## Hechos

- El merge solo ocurre con veredicto APROBADO.
- Los casos de prueba de eventos validos e invalidos viven en `contracts/examples/`.
- Los problemas recurrentes encontrados se anotan abajo para vigilarlos en fases siguientes.

## Problemas recurrentes

- Fase 00: el GIT_GUIDE omitio comandos pedidos por el prompt. Contrastar siempre la lista literal de entregables con el documento.
- Los comandos de busqueda con `--include=*.md` fallan en zsh si no se entrecomillan. Usar `find` con `-name` y `xargs grep`.

- Fase 01: las decisiones nuevas de una fase no llegaron a `docs/DECISIONS.md`. Verificar siempre que cada decision citada en el reporte este registrada ahi.
- Al escribir comprobaciones, cuidar los valores ausentes: comparar `None` con `None` produce falsos positivos.
- El `grep` de macOS no soporta `-P`. Para emojis usar Python.

## Herramientas utiles

- Cruzar tablas de documentos Markdown contra los JSON esperados con un script, no a ojo.
- Recalcular patrones por fuerza bruta (rachas con groupby, marcador acumulado) como segunda opinion.

- Validacion de ejemplos con Ajv 8 (2020-12) y ajv-formats instalados fuera del repositorio.

- Fase 02: el eventId se registra como visto antes de encolar; si el encolado falla, el reintento del cliente recibe 409. Vigilar el orden deduplicar y encolar en servicios de ingesta.
- Al verificar el orden por timestamp, parsear las fechas; comparar texto falla porque las fracciones no tienen ancho fijo.

- Fase 03: las pruebas comparan el expected.json solo en campos genericos; verificar que cubran todos los campos (ganador incluido) y que los agregados se contrasten con docs/STATS_EXPECTED_RESULTS.md cuando existan analizadores reales.
