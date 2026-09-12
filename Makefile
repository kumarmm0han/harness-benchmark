.PHONY: demo verify smoke
demo:
	docker compose up --build -d --wait --wait-timeout 180
verify:
	python3 scripts/check-inputs.py
	docker compose config --quiet
	./scripts/backend-verify.sh
	npm --prefix frontend ci --no-audit --no-fund
	npm --prefix frontend test
	npm --prefix frontend run typecheck
	npm --prefix frontend run lint
	npm --prefix frontend run build
smoke:
	python3 scripts/smoke.py
	npm --prefix frontend run test:e2e
