# CodeForge AI

[中文](README.md)

CodeForge AI is a secure and auditable full-stack platform for AI application generation, artifact lifecycle management, marketplace publishing, and administrative observability.

![CodeForge AI product overview](docs/images/codeforge-overview.webp)

## Highlights

- Versioned prompt templates bound to generation tasks and model call logs.
- Governed model provider routing with rule fallback for local validation.
- Traceable generated files, app versions, repair versions, exports, and marketplace publications.
- Owner/editor/admin authorization boundaries for private artifacts and downloads.
- MySQL migration topology with B33 baseline support for fresh environments.

## Local Development

`docker-compose.yml` starts infrastructure only: MySQL and Redis.

```powershell
docker compose up -d mysql redis
powershell -File .\scripts\db\check-local-schema.ps1
powershell -File .\scripts\dev-start.ps1 -Profile local -BackendPort 8150 -FrontendPort 5182
```

Frontend: `http://127.0.0.1:5182`

Backend API: `http://127.0.0.1:8150/api`

## Documentation

- [Architecture](docs/architecture.md)
- [Product Tour](docs/product-tour.md)
- [Configuration](docs/configuration.md)
- [Security Model](docs/security-model.md)
- [Deployment](docs/deployment.md)
- [Troubleshooting](docs/troubleshooting.md)

## Verification

```powershell
mvn test
Push-Location frontend
npm ci
npm run type-check
npm run test
npm run build
Pop-Location
node --test scripts/release/**/*.test.mjs
git diff --check
```

## License

MIT License. Maintained by [@18307519324az](https://github.com/18307519324az).
