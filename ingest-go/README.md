# ingest-go

Modulo Go de ingesta. Recibe eventos, los valida, los agrupa por partida con una goroutine por partida y envia cada partida completa y ordenada a `analytics-scala`.

Puerto: 8080. Contrato: secciones 5, 6 y 7 de `docs/BLUEPRINT.md`.

Pendiente: implementar en la fase 02.
