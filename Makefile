COMPOSE := docker compose -f deploy/docker-compose.yml

.PHONY: build test up down simulate simulate-files inbox-add inbox-demo smoke logs

build:
	$(COMPOSE) --profile simulation build

test:
	cd ingest-go && go vet ./... && go test -race ./...
	cd simulator && go vet ./... && go test -race ./...
	cd analytics-scala && sbt -batch test

up:
	$(COMPOSE) up -d --build --wait

down:
	$(COMPOSE) --profile simulation down --volumes --remove-orphans

simulate:
	$(COMPOSE) --profile simulation run --rm simulator
	@echo "Esperando a que Go termine de entregar las partidas a Scala"
	@for attempt in $$(seq 1 40); do curl -fs localhost:8080/metrics | grep -q '"activeMatches":0' && break; sleep 1; done
	@sleep 2
	@echo "Panel web de resultados: http://localhost:8081"

simulate-files:
	$(COMPOSE) --profile simulation run --rm -e SIMULATOR_OUTPUT_DIRECTORY=/inbox simulator
	@echo "Esperando a que Go lea los archivos de la carpeta inbox"
	@sleep 3
	@for attempt in $$(seq 1 40); do curl -fs localhost:8080/metrics | grep -q '"activeMatches":0' && break; sleep 1; done
	@sleep 2
	@echo "Panel web de resultados: http://localhost:8081"

inbox-add:
	@test -n "$(FILE)" || (echo "Uso: make inbox-add FILE=ruta/al/archivo.json" && exit 1)
	$(COMPOSE) cp "$(FILE)" ingest-go:/inbox/

inbox-demo:
	$(COMPOSE) cp contracts/examples/games/racing/patterns-triggered.batch.json ingest-go:/inbox/carrera-ejemplo.json
	$(COMPOSE) cp contracts/examples/games/combat/patterns-triggered.batch.json ingest-go:/inbox/combate-ejemplo.json
	@sleep 4
	@echo "Panel web de resultados: http://localhost:8081"

smoke:
	./qa/smoke.sh

logs:
	$(COMPOSE) logs --follow
