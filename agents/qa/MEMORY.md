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
