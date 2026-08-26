# OXYGEN AI

**Local-First Personal Agentic AI for Android**

OXYGEN AI is a personal AI agent that can communicate, remember, retrieve knowledge, use tools, search the web, work with documents, execute bounded multi-step tasks, and run a local language model directly on Android.

It is **not** a chatbot skin and **not** an LLM web frontend. The Chat UI is one interface. The **Agent Core** is the system.

- Package: `com.oxygen.ai`
- Default local model: `Qwen3-4B-Q4_K_M.gguf` (imported by the user, never bundled)
- Inference: `llama.cpp` via NDK / JNI
- UI: Kotlin + Jetpack Compose + Material 3 + OXYGEN design system

## What this project is

A native Android Gradle project. Import the root folder in Android Studio (File → Open).

The same Agent Core is used by:

- the Android UI
- the Telegram bridge
- future voice / web / API clients

No Node.js, Python, Termux, React Native, Expo, or Flutter runtime is required.

## Quick start

1. Install Android Studio with SDK 35, NDK 27, CMake 3.22.1, JDK 17.
2. Copy `local.properties.example` to `local.properties` and set `sdk.dir`.
3. Open the project. First native configure fetches `llama.cpp` (needs git + network).
4. Run the **debug** configuration on an **ARM64** device or emulator with plenty of RAM.
5. In **Models**, import a GGUF (recommended: Qwen3-4B Q4_K_M).
6. Load the model, then chat.

See [BUILD.md](BUILD.md) and [ARCHITECTURE.md](ARCHITECTURE.md).

## Capabilities

| Area | Behaviour |
| --- | --- |
| Local LLM | llama.cpp, streaming, cancel, unload, switch |
| Agent Core | bounded plan → retrieve → tools → generate |
| Context Engine | budget, rank, compress — does **not** invent extra tokens |
| Memory | Room, relevance retrieval, merge/update/delete |
| RAG / PDF | parse → chunk → embed → retrieve → cite pages |
| MCP | JSON-RPC client, permissions, HTTP/SSE |
| Web search | SearXNG-compatible provider, citations |
| Telegram | same Agent Core, allowlist, optional foreground service |
| Drive | backup/sync layer; **not** the runtime database |
| Offline | local chat, memory, RAG, models work without network |

## Non-negotiables

- No cloud LLM is required, and none is used as a silent fallback.
- No fake inference, memory, RAG, MCP, search, PDF, or Drive.
- UI composables contain no business logic.
- External web / document / MCP content is untrusted.
- Secrets live in Android Keystore-backed storage.

## License

Application source is provided as the OXYGEN AI project deliverable. Third-party licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). `llama.cpp`, GGUF models, and other dependencies remain under their own licenses. Do not commit model binaries.
