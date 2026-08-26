# AGENT_CORE.md

The Agent Core is the only orchestration brain.

## Types

- `AgentCore` — reusable `AgentSink` used by Android and Telegram
- `AgentOrchestrator` — the loop
- `AgentSession` — per-turn state
- `TaskPlanner` — complexity + plan
- `ToolPlanner` — initial tools + parse model tool calls
- `ContextPlanner` — packs retrieval into the Context Engine
- `ExecutionManager` — timeouts, truncation, permissions
- `ReasoningController` — maps user level + complexity → profile

## States

`IDLE THINKING PLANNING RETRIEVING_MEMORY RETRIEVING_RAG CALLING_TOOL SEARCHING_WEB PROCESSING_RESULT GENERATING WAITING COMPLETED FAILED CANCELLED`

The UI shows status labels such as “Searching documents…”. Hidden chain-of-thought is stripped (`<think>…</think>`).

## Limits

Configured by `AgentLimits` and the active `ReasoningBudget`:

- `maxToolCalls`
- `maxIterations`
- `maxExecutionTimeMs`
- `maxContextTokens`
- `maxToolOutputSize`
- `maxRetries`

On limit: **stop, explain, return partial result**. No uncontrolled recursion.

## Tool calling

1. Planner may schedule `rag_search`, `memory_search`, `web_search`, `calculator`, `datetime`, `telegram_send`.
2. After a generation, `ToolPlanner` parses `<tool_call>…</tool_call>` and ` ```json ` blocks.
3. Unknown tools are ignored. Disabled / unpermitted tools fail closed.

The orchestrator does not care whether a tool is builtin, MCP, or remote.
