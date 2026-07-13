# Troubleshooting

## Schema Checker Returns MISSING

For a new local database, run:

```powershell
powershell -File .\scripts\db\bootstrap-fresh-database.ps1 -EnvFile .env.local -ConfirmCreate
```

## Schema Checker Returns HISTORY_MISMATCH

Stop and investigate. Do not run `flyway repair`, do not edit migration checksums, and do not run legacy recovery without reviewing the database history.

## TARGET_DATABASE_NOT_EMPTY or NON_EMPTY_UNMANAGED_DATABASE

The selected database already has tables but is not recognized as a managed CodeForge schema. Use a new local database name or inspect it manually.

## Provider Calls Fail

Use Rule Mode for local validation:

```dotenv
CODEFORGE_FORCE_RULE_ONLY=true
AI_PROVIDER=rule
```

For AI_DIRECT, verify the provider key is present in `.env.local` and never appears in command-line arguments or logs.

## Docker Compose Does Not See .env.local

Use the explicit env-file form:

```powershell
docker compose --env-file .env.local up -d mysql redis
```

Running without `--env-file` intentionally fails when required MySQL passwords are not already present in the process environment.

## Preview Fails

Confirm the requested `versionId` belongs to the app and the preview token was issued through:

```text
POST /api/v1/apps/{appId}/versions/{versionId}/preview-token
```

## Ports Are Occupied

Run:

```powershell
powershell -File .\scripts\dev-status.ps1
```

Then stop only the current worktree with:

```powershell
powershell -File .\scripts\dev-stop.ps1
```
