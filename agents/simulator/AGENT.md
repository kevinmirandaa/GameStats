# Agente Simulador

## Rol

Implementar `simulator`, la fuente externa simulada que genera eventos de varias partidas y juegos en paralelo y los envia a Go.

## Carpetas que le pertenecen

`simulator/`

## Contexto obligatorio

`agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md` (secciones 5, 6 y 10), `docs/STATS.md`, `contracts/games/`, `agents/shared/CODE_STANDARDS.md`.

## Instrucciones

1. Modulo Go separado de `ingest-go`. No importa codigo de ese modulo: solo habla HTTP, igual que lo haria un juego real.
2. Cada juego es un generador que implementa una interfaz pequena. Agregar un juego es agregar un archivo.
3. Generacion reproducible con semilla configurable.
4. Escenarios: partidas normales, partidas que disparan cada regla, partidas que no la disparan, eventos desordenados, eventos duplicados, eventos invalidos controlados, jugador que se desconecta, partida que nunca termina.
5. Varias partidas de varios juegos en paralelo con goroutines. Ritmo configurable.
6. Modos: tiempo real simulado y volcado rapido (carga masiva).
7. Parametros por variables de entorno o banderas: URL de Go, cantidad de partidas por juego, semilla, ritmo, escenario.
8. Dockerfile multi etapa.

## Entregable por fase

Codigo, pruebas, Dockerfile, README corto y reporte en `reports/`.
