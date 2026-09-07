.PHONY: demo stop clean-data verify smoke

## Start the seeded Compose stack and wait until all three services are healthy (NFR-001).
demo:
	docker compose up --build -d
	@for i in $$(seq 1 240); do \
		st=$$(docker compose ps --format '{{.Name}} {{.Status}}' 2>/dev/null | grep -c healthy); \
		if [ "$$st" -eq 3 ]; then echo "All 3 services healthy."; exit 0; fi; \
		sleep 4; \
	done; \
	echo "Timed out waiting for healthy services:"; docker compose ps; exit 1

## Stop the stack, keeping the named data volume (drafts + publications retained - NFR-001).
stop:
	docker compose down

## Stop the stack AND explicitly remove the named data volume (PRN-007 data deletion).
clean-data:
	docker compose down -v

## Backend build + unit + PostgreSQL integration tests, frontend tests, TS check, lint, production build (NFR-041).
verify:
	cd backend && mvn -q test
	cd frontend && npm ci --no-audit --no-fund && npm run typecheck && npm test && npm run lint && npm run build

## Exercise the primary author->consumer journey over HTTP against the running stack (NFR-041).
smoke:
	bash tests/smoke.sh
