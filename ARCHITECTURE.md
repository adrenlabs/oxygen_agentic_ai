# ARCHITECTURE.md

```
                    OXYGEN AI
                        │
                 ┌──────▼──────┐
                 │  Agent Core │
                 └──────┬──────┘
                        │
   ┌────────────────────┼────────────────────┐
   │                    │                    │
Context Engine       Memory             Tool System
   │                    │                    │
  RAG              Drive Sync        ┌───────┼────────┐
   │                    │            │       │        │
  PDF              Persistence      MCP     Web    Telegram
   │
   └────────────────────┐
                        │
                  Model Runtime
                        │
                    llama.cpp
                        │
                  Qwen3-4B GGUF
```

## Rules

- The UI is never the business-logic layer.
- Telegram is never the backend.
- Google Drive is never the runtime database.
- llama.cpp is never called from composables.
- Cloud LLMs are never a silent fallback.

## Modules (packages)

| Package | Role |
| --- | --- |
| `com.oxygen.ai.agent` | Agent Core, planner, execution loop |
| `com.oxygen.ai.context` | Context Engine |
| `com.oxygen.ai.reasoning` | Reasoning levels and task modes |
| `com.oxygen.ai.inference` | Runtime interfaces + llama.cpp JNI |
| `com.oxygen.ai.models` | Model manager / profiles |
| `com.oxygen.ai.memory` | Long-term memory |
| `com.oxygen.ai.rag` / `pdf` | Documents, embeddings, PDF |
| `com.oxygen.ai.tools` | Unified tool abstraction |
| `com.oxygen.ai.mcp` | MCP client |
| `com.oxygen.ai.search` | Web search providers |
| `com.oxygen.ai.telegram` | Telegram gateway |
| `com.oxygen.ai.drive` | Sync layer |
| `com.oxygen.ai.data` | Room / SQLite |
| `com.oxygen.ai.ui` | Compose + OXYGEN design system |
| `com.oxygen.ai.di` | Composition root (`OxygenGraph`) |

## Request path

```
User (Android or Telegram)
        ↓
   Agent Core
        ↓
 Task classification → Reasoning profile
        ↓
 Memory / RAG / Web / Tools   (bounded)
        ↓
 Context Engine  (what enters the window)
        ↓
 llama.cpp streaming
        ↓
 Response + citations + optional memory write
```

## Persistence

Runtime truth is **Room / SQLite** (`oxygen.db`). Drive is an optional, user-enabled replica under `OXYGEN/{memory,conversations,documents,embeddings,backups,settings,metadata}`.

## Extensibility

New tools implement `Tool` and register on `ToolRegistry`. New search backends implement `SearchProvider`. New embeddings implement `EmbeddingProvider`. Vision / voice / calendar can attach later without rewriting Agent Core.
