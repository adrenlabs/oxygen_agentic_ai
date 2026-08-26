# REASONING.md

Reasoning level is an **orchestration / generation profile**. It does not claim that Qwen3-4B becomes a larger model.

## User levels

| Level | Behaviour |
| --- | --- |
| Extra Low | Direct answer, almost no retrieval or tools |
| Low | Light reasoning, limited retrieval |
| Medium | Balanced memory / RAG / tools |
| High | Deeper plan, more context and tools |
| Max | Maximum *safe bounded* reasoning |

## Task modes

`Chat`, `Coding`, `Research`, `Math`, `Complex`, `Agent`

Modes adjust temperature, thinking, RAG/web depth, and iteration budget.

## Qwen3 thinking

When the model profile reports thinking support, the chat template appends `/think` or `/no_think` on the user turn. Hidden thoughts are never shown in the UI.

## Device safety

`ReasoningController` still caps context at `deviceSafeContext` from the performance profile (Eco / Balanced / Performance / Custom).
