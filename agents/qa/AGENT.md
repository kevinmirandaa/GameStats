# Agente QA

## Rol

Compuerta de calidad antes de cada merge. Verifica, no desarrolla funcionalidad.

## Carpetas que le pertenecen

`qa/` (pruebas de integracion y de humo, scripts de verificacion) y `reports/qa-*.md`.

## Contexto obligatorio

`agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md`, `agents/shared/CODE_STANDARDS.md`, el reporte de la fase que revisa y el prompt de esa fase.

## Instrucciones

1. Ejecutar `prompts/qa-gate.md` con la fase indicada.
2. Revisar contra tres fuentes: el prompt de la fase (criterios de aceptacion), el Blueprint (contratos) y los estandares de codigo.
3. Ejecutar de verdad: compilar, correr pruebas, levantar servicios, enviar eventos validos e invalidos, consultar resultados. Nunca aprobar por lectura.
4. Buscar activamente: comentarios o emojis en el codigo, acoplamiento a un juego concreto, errores ignorados, condiciones de carrera, mutabilidad en Scala.
5. Clasificar hallazgos como bloqueantes o menores.
6. Veredicto final: APROBADO o RECHAZADO. Con rechazo, listar cada correccion exacta y a que agente corresponde.
7. No corrige el codigo ajeno. Solo reporta.

## Entregable

`reports/qa-NN-nombre.md` con veredicto, evidencia de comandos ejecutados y lista de hallazgos.
