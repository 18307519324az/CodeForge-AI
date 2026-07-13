# Configuration

All real secrets must come from `.env.local`, process environment, or a managed secret system. They must not be committed, logged, pasted into Issues, or shown in screenshots.

## Env File

Copy the sample file:

```powershell
Copy-Item .env.example .env.local
```

`scripts/db/bootstrap-fresh-database.ps1`, `scripts/dev-start.ps1`, and the Bash dev scripts load `.env.local` as literal key/value configuration. The PowerShell loader accepts `KEY=VALUE`, comments, blank lines, and quoted strong passwords containing `&`, `|`, `{`, `}`, `$`, and spaces. It rejects malformed quoted values, command substitution, backtick expressions, duplicate variables, and unsupported variable names.

## Required Local Values

| Variable | Purpose |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | Docker MySQL root password |
| `MYSQL_PASSWORD` | Docker app-user password |
| `DB_PASSWORD` | Backend database password |
| `JWT_SECRET` | JWT signing secret |
| `CODEFORGE_CREDENTIAL_MASTER_KEY` | Provider credential encryption key |

## Database

| Variable | Example | Purpose |
| --- | --- | --- |
| `DB_HOST` | `127.0.0.1` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `codeforge_ai` | Local schema name |
| `DB_USERNAME` | `codeforge_ai_user` | App database user |
| `DB_PASSWORD` | `<local-password>` | App database password |

## Redis

| Variable | Example | Purpose |
| --- | --- | --- |
| `REDIS_HOST` | `127.0.0.1` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | empty | Optional local password |

## Provider

For deterministic local validation:

```dotenv
CODEFORGE_FORCE_RULE_ONLY=true
AI_PROVIDER=rule
```

For real provider calls:

```dotenv
CODEFORGE_FORCE_RULE_ONLY=false
AI_PROVIDER=auto
```

Then configure the matching provider key. Do not put provider keys in command-line arguments.

## Fresh Database

Use:

```powershell
powershell -File .\scripts\db\bootstrap-fresh-database.ps1 -EnvFile .env.local -ConfirmCreate
```

Expected output includes `B33_BASELINE_APPLIED`, `FLYWAY_VALIDATE_PASS`, and `SCHEMA_STATUS=READY`.

If the target database does not exist, the default path returns `TARGET_DATABASE_DOES_NOT_EXIST`. Explicit creation requires `-CreateDatabase` with `DB_ADMIN_USERNAME` and `DB_ADMIN_PASSWORD`; migration still uses `DB_USERNAME` and `DB_PASSWORD`.

## Legacy Recovery

`scripts/db/apply-local-migrations.ps1` is only for manually reviewed `EXPERIMENTAL_LEGACY_RECOVERY` and requires `-ConfirmLegacyRecovery`.
