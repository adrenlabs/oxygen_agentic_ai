# MCP.md

OXYGEN is an **MCP client**.

## Types

`McpClientManager`, `McpServerManager`, `McpConnection`, `McpProtocol`, transports, `ToolPermissionManager`.

## Protocol

JSON-RPC 2.0 (`2024-11-05`):

- `initialize` / `notifications/initialized`
- `tools/list`, `tools/call`
- `resources/list`, `resources/read`
- `prompts/list`, `prompts/get`
- `ping`, `notifications/cancelled`

## Transports

Modular: Streamable HTTP and SSE. Stdio process servers are not assumed on Android.

No server is hardcoded. Users add endpoints in the MCP screen.

## Security

Each server/tool: `Disabled` / `Ask` / `Allowed`. Destructive calls need an explicit allow. Output size is capped. MCP descriptions are not trusted as system policy.

Discovered tools are registered on the same `ToolRegistry` as builtins.
