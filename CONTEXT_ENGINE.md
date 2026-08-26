# CONTEXT_ENGINE.md

`OxygenContextEngine` decides **what enters** the model. It does not change architecture and does not invent a custom token extender.

## Pipeline

```
User request → recent conversation → memory → RAG → tool results → web
        → rank / filter / dedupe → compress → prompt builder → Qwen
```

## Profiles

`8K`, `16K`, `32K Native`, `Extended`

Default is dynamic: device RAM, model metadata, workload, and pressure pick a **safe** window. Extended / YaRN is used only when enabled **and** necessary.

## Budget

From the usable window (`total - outputReserve`):

- system ~12%
- memory ~10% (if present)
- RAG ~18%
- tools ~12%
- web ~10%
- remainder → history (older turns compressed)

## Ranking

Hybrid BM25-ish lexical score + recency + importance. RAG also uses cosine on stored embeddings.

## Untrusted channels

Memory, RAG, web, MCP, and tool output are wrapped by `PromptInjectionDefense` so they cannot silently become system instructions.
