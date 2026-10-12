# Fase 07 QA integral

Agente: qa

## Leer primero

`agents/qa/AGENT.md`, `agents/qa/MEMORY.md`, `agents/shared/CODE_STANDARDS.md`, `docs/BLUEPRINT.md`, `docs/STATS.md`, todos los reportes en `reports/`.

## Objetivo

Verificar el sistema completo contra el enunciado y el Blueprint, y reportar cualquier brecha.

## Entregables

1. Matriz de cumplimiento del enunciado: cada funcionalidad minima (1 a 10), cada requisito de Go, de Scala y de infraestructura, con evidencia ejecutada.
2. Pruebas de extremo a extremo en `qa/`: eventos validos, invalidos, duplicados y desordenados; muchas partidas en paralelo; partida sin MATCH_FINISHED; Scala caido temporalmente.
3. Prueba de genericidad: enviar eventos de un juego ficticio nuevo y comprobar que se aceptan, se agrupan y generan analisis generico sin cambiar codigo.
4. Revision de estandares de codigo: busqueda de comentarios, emojis, lineas decorativas, `var`, `null`, errores ignorados.
5. Prueba de carga moderada y registro de tiempos.
6. Lista de brechas con prioridad y agente responsable.

## Criterios de aceptacion

- Cada afirmacion tiene evidencia de un comando ejecutado.
- El veredicto final es APROBADO solo si no hay bloqueantes.

## Fuera de alcance

Corregir el codigo ajeno.

## Reporte

Escribir `reports/qa-07-integral.md`. Mostrar el resumen en pantalla.
