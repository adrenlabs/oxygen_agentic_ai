# TROUBLESHOOTING.md

## Gradle sync fails

- JDK 17 required (`Settings → Build → Gradle JDK`).
- Set `sdk.dir` in `local.properties`.
- Invalidate caches if the version catalog cannot resolve Google artifacts.

## Native / CMake / llama.cpp

- Install NDK + CMake from SDK Manager.
- First configure needs git + network to fetch llama.cpp.
- If fetch fails, check firewall / git HTTPS.
- Set `oxygen.enableLlama=false` only for UI work; generation will be unavailable.

## Model will not load

- Confirm the file is GGUF (metadata screen / import error).
- Qwen3-4B Q4_K_M needs several GB of free RAM and storage.
- Try Eco performance profile or a smaller context (Settings → Performance).
- `ModelCorrupted` means checksum or header failure — re-download.

## Generation is slow / thermal

- Expected on CPU. Use Eco, fewer threads, smaller context.
- Stop is cooperative via JNI cancel.

## RAG / PDF empty

- Scanned-image PDFs have no text layer. Extraction will fail or be empty — that is not faked OCR.
- Encrypted PDFs without a password are rejected.

## Web search

- Configure a SearXNG base URL.
- Disable Local Only and enable Web search.
- Device must be online.

## Telegram

- Store the bot token in Settings / Telegram (Keystore).
- Add your numeric user id to the allowlist.
- Start the gateway explicitly. Android will show a persistent notification.

## Drive

- Local Only uploads nothing (by design).
- Sign-in tokens must be placed via the secret store; missing token → `AuthenticationFailed`.
- Conflicts never blindly overwrite.

## Offline

Local chat, memory, RAG, and model management keep working. Network-only tiles disable with a clear error.
