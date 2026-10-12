# Compuerta de calidad antes de merge

Agente: qa

Se ejecuta indicando el numero de fase. Ejemplo: "fase 02".

## Leer primero

`agents/qa/AGENT.md`, `agents/qa/MEMORY.md`, `agents/shared/CODE_STANDARDS.md`, `docs/BLUEPRINT.md`, el prompt de la fase indicada en `prompts/`, el reporte de esa fase en `reports/` y los archivos cambiados en la rama (`git diff main --stat`).

## Procedimiento

1. Identificar los criterios de aceptacion del prompt de la fase.
2. Ejecutar cada criterio con comandos reales y guardar la salida relevante.
3. Revisar el codigo cambiado contra los estandares: comentarios, emojis, separadores decorativos, nombres poco descriptivos, funciones largas, duplicacion, errores ignorados, `var` o `null` en Scala, acoplamiento a juegos concretos en el nucleo.
4. Probar con datos de `contracts/examples/` cuando la fase maneje eventos.
5. Verificar que los archivos modificados pertenecen al agente de la fase.
6. Clasificar hallazgos en bloqueantes y menores.

## Resultado

Escribir `reports/qa-NN-nombre.md` con:

- Veredicto: APROBADO o RECHAZADO.
- Tabla de criterios con resultado y evidencia.
- Hallazgos bloqueantes con la correccion exacta y el agente responsable.
- Hallazgos menores.
- Si RECHAZADO, un bloque listo para copiar al agente correspondiente con las correcciones.

Mostrar el veredicto y los bloqueantes en pantalla. Actualizar `agents/qa/MEMORY.md` si se detecto un problema recurrente.
