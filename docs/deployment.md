# Deployment

## Local Development

1. Copy `.env.example` to `.env.local`.
2. Set local passwords and secrets.
3. Start infrastructure:

```powershell
docker compose up -d mysql redis
```

4. Initialize a fresh MySQL database:

```powershell
powershell -File .\scripts\db\bootstrap-fresh-database.ps1 -EnvFile .env.local -ConfirmCreate
```

5. Start backend and frontend:

```powershell
powershell -File .\scripts\dev-start.ps1 -Profile local -EnvFile .env.local -BackendPort 8150 -FrontendPort 5182
```

## Fresh DB

Fresh DB initialization is only `scripts/db/bootstrap-fresh-database.ps1`. It loads `.env.local` through the safe EnvFile parser, runs Flyway `info`, `migrate`, `validate`, and then `check-local-schema.ps1`.

## Legacy Recovery

`scripts/db/apply-local-migrations.ps1` is `EXPERIMENTAL_LEGACY_RECOVERY`. It requires `-ConfirmLegacyRecovery` and is only for manually reviewed historical local databases. It must not be used as a fresh clone setup command.

## Production

This repository does not ship a full production image. Production use must provide hardened secrets management, TLS, backup policy, object storage policy, logging policy, and provider credential rotation.
