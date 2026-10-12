COMPOSE := docker compose -f deploy/docker-compose.yml

.PHONY: build test up down simulate smoke logs

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

smoke:
	./qa/smoke.sh

logs:
	$(COMPOSE) logs --follow
