# TELEGRAM.md

Telegram is another **interface**, not a backend.

```
Telegram → TelegramGateway → Agent Core → Memory/RAG/MCP/Web → local Qwen → Telegram
```

## Pieces

`TelegramGateway`, `TelegramBotAdapter`, `TelegramRouter`, `TelegramSession`, optional `TelegramService` (foreground, user started).

## Behaviour

- text and slash commands (`/start /help /model /reason`)
- allowlist (everyone else is dropped)
- same reasoning / model hints
- `telegram_send` builtin tool for outbound messages
- independently disableable
- token stored only in Keystore
- no direct llama.cpp access

Background: the gateway uses a user-controlled foreground service (`remoteMessaging`). There is no hidden always-on daemon.
