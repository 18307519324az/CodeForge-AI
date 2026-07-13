# Deployment

## Local Development

1. Copy `.env.example` to `.env.local`.
2. Set local passwords and secrets.
3. Start infrastructure:

```powershell
docker compose --env-file .env.local up -d mysql redis
```

4. Initialize a fresh MySQL database:

```powershell
powershell -File .\scripts\db\bootstrap-fresh-database.ps1 -EnvFile .env.local -ConfirmCreate
```

5. Start backend and frontend:

```powershell
powershell -File .\scripts\dev-start.ps1 -Profile local -EnvFile .env.local -BackendPort 8150 -FrontendPort 5182
```

Linux/macOS:

```bash
ENV_FILE=.env.local PROFILE=local BACKEND_PORT=8150 FRONTEND_PORT=5182 ./scripts/dev-start.sh
```

## Fresh DB

Fresh DB initialization is only `scripts/db/bootstrap-fresh-database.ps1`. It loads `.env.local` through the safe EnvFile parser, runs Flyway `info`, `migrate`, `validate`, and then `check-local-schema.ps1`. The default path initializes an existing empty database; a missing target database returns `TARGET_DATABASE_DOES_NOT_EXIST` unless `-CreateDatabase` is used with `DB_ADMIN_USERNAME` and `DB_ADMIN_PASSWORD`.

## Legacy Recovery

`scripts/db/apply-local-migrations.ps1` is `EXPERIMENTAL_LEGACY_RECOVERY`. It requires `-ConfirmLegacyRecovery` and is only for manually reviewed historical local databases. It must not be used as a fresh clone setup command.

## Production

This repository does not ship a full production image. Production use must provide hardened secrets management, TLS, backup policy, object storage policy, logging policy, and provider credential rotation.
