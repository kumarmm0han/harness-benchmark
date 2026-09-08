# SOP demo local operations (DES-017, NFR-001).
# `make verify` / `make smoke` are added in TASK-010.

COMPOSE ?= docker compose

# Composable, overridable project name (defaults to `sopdemo`) so multiple instances can
# coexist and the stack never clobbers an unrelated run.
COMPOSE_PROJECT_NAME ?= sopdemo
export COMPOSE_PROJECT_NAME

# Non-default host ports, overridable (DES-017).
SOPDEMO_BACKEND_HOST_PORT ?= 18080
SOPDEMO_UI_HOST_PORT ?= 13000
SOPDEMO_DB_HOST_PORT ?= 15432
export SOPDEMO_BACKEND_HOST_PORT SOPDEMO_UI_HOST_PORT SOPDEMO_DB_HOST_PORT

.DEFAULT_GOAL := help

.PHONY: help
help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-8s\033[0m %s\n", $$1, $$2}'

.PHONY: demo
demo: ## Build and start the seeded stack; wait until all three services report healthy
	$(COMPOSE) up --build --wait

.PHONY: down
down: ## Stop the stack (keeps the named data volume)
	$(COMPOSE) down

.PHONY: logs
logs: ## Follow the stack logs
	$(COMPOSE) logs -f

.PHONY: clean
clean: ## Stop the stack and remove the named data volume (explicit data deletion)
	$(COMPOSE) down -v
