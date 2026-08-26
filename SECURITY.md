# SECURITY.md

## Secrets

`SecretStore` uses Android Keystore + EncryptedSharedPreferences.

Never hardcoded: API keys, Telegram token, Google tokens, MCP secrets.

Logs pass through `SecretRedactor`. Diagnostics never print credentials.

## Files

`PathSafety` blocks traversal and oversized imports. Attachments and models stay under app-private directories.

## Prompt injection

Channels are separated: System, User, Trusted OXYGEN, Memory, RAG, Web, MCP, Tool output.

Untrusted text cannot change:

- system policy
- user permissions
- tool permission rules
- security constraints

Tool execution is decided by Agent Core + `ToolPermissionManager`, not by a document that says “run this”.

## Network

Cleartext is off except localhost. Optional integrations are independently switchable. Local Only mode refuses Drive / Telegram / web / remote MCP.

## Backup

Auto cloud backup of the database and secret prefs is excluded in the backup XML rules.
