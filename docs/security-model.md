# Security Model

## Authorization Boundary

Private apps, app versions, generated files, export packages, and marketplace publication records are read through owner/workspace/app/version/package binding checks. Admin authority does not remove archived/unpublished checks or storage path boundaries.

## Prompt and Model Calls

Generation tasks persist `templateId` and `templateVersionId`. Runtime calls load the fixed version for async execution and retries. Model call logs store template identity and prompt fingerprints, not full system prompts.

## Storage Path Boundary

Generated artifact paths are validated segment by segment. `..`, absolute paths, Windows drives, UNC paths, and NUL bytes are rejected. Final paths are normalized and constrained to the version root.

## Preview

Preview tokens are issued for exact `versionId` values. Preview APIs serve files by version and relative file path, never by raw storage path.

## Export and Marketplace

Export packages bind app, version, and package identity. Marketplace publications pin a `versionId`; detail, preview, and download use that same pinned version and enforce archived/unpublished state on every read.

## Audit Safety

Audit logs record actor, action, object identity, timestamps, and safe metadata. They must not include provider keys, JWTs, cookies, full prompts, local paths, or user passwords.

## Vulnerability Reporting

Security vulnerabilities must be reported privately through:

https://github.com/18307519324az/CodeForge-AI/security/advisories/new

Public Issues are only for ordinary non-security bugs.
