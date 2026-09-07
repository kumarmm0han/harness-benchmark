# SOP demo — reproducible local operation (NFR-001, NFR-041, TASK-003/019).
# Local-only: no external services; all state in the `sop_pgdata` Compose volume.

COMPOSE := docker compose -f compose/docker-compose.yml
BASE    := http://localhost:8075

.PHONY: help demo down down-clean verify smoke backend-test backend-build frontend-test frontend-build typecheck lint

help:
	@echo "SOP demo targets:"
	@echo "  make demo         Build + start the three healthy services (db, backend, frontend)"
	@echo "  make smoke        Run the primary author→consumer journey against the running stack"
	@echo "  make verify       backend tests + frontend tests + typecheck + lint + prod build"
	@echo "  make backend-test mvn test (unit + PostgreSQL integration)"
	@echo "  make frontend-test vitest run (jsdom)"
	@echo "  make typecheck    npx tsc --noEmit (frontend)"
	@echo "  make lint         eslint (frontend)"
	@echo "  make down         Stop + remove the stack (keeps the volume)"
	@echo "  make down-clean   Stop + remove the stack AND the named volume (DELETE ALL DATA)"

demo:
	$(COMPOSE) up --build -d
	@scripts/wait-for-health.sh "$(BASE)" 90

backend-build:
	cd backend && mvn -q -f pom.xml -DskipTests package

backend-test:
	cd backend && mvn -q -f pom.xml test

frontend-test:
	cd frontend && npm ci --no-audit --no-fund && npm test

frontend-build:
	cd frontend && npm run build

typecheck:
	cd frontend && npx tsc --noEmit

lint:
	cd frontend && npm run lint

verify: backend-test frontend-test typecheck lint
	cd frontend && npm run build
	@echo "verify: OK"

smoke:
	@$(COMPOSE) ps | grep -q healthy || $(COMPOSE) up -d
	@scripts/wait-for-health.sh "$(BASE)" 90
	@scripts/smoke.sh "$(BASE)"

down:
	$(COMPOSE) down

down-clean:
	$(COMPOSE) down -v
