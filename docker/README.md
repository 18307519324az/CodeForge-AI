# Docker

`docker-compose.yml` provides local infrastructure only:

- MySQL 8
- Redis 7

It does not build or run the Spring Boot backend, and it does not build or run the Vue frontend.

## Local Infrastructure

```powershell
docker compose --env-file .env.local up -d mysql redis
```

Then initialize the database with the official Fresh Bootstrap script:

```powershell
powershell -File .\scripts\db\bootstrap-fresh-database.ps1 -EnvFile .env.local -ConfirmCreate
```

Start application processes from the repository root:

```powershell
powershell -File .\scripts\dev-start.ps1 -Profile local -EnvFile .env.local -BackendPort 8150 -FrontendPort 5182
```

Linux/macOS users can start the application processes with:

```bash
ENV_FILE=.env.local PROFILE=local BACKEND_PORT=8150 FRONTEND_PORT=5182 ./scripts/dev-start.sh
```

## Production Gap

Production deployment still requires a hardened backend image, static frontend hosting, reverse proxy, TLS, object storage policy, secret management, backup strategy, and audit log retention.
