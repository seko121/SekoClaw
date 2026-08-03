# Post-Kai integration fixes

## Heartbeat crash

The reproduced crash was `IllegalArgumentException` from Compose's `SaveableStateRegistry`: `HeartbeatConfig` was placed in `rememberSaveable` without a `Saver`, although it is not a Bundle-supported type. The screen now keeps the aggregate object in ordinary retained Compose state, persists individual primitive settings through `HeartbeatManager`, sanitizes old/corrupt values, restores a non-empty default prompt, and surfaces scheduling failures in the page. `HeartbeatManagerTest` protects the legacy-value sanitization.

## Model favorites and routing

- Remote fetched models are marked non-manual and are never given destructive delete controls.
- `isFavorite` is persisted per model. Favorite adds once to Routing; unfavorite removes it and refuses to leave the user without any routed model without warning.
- Routing is de-duplicated. Position 1 is persisted as `activeModelId`, the legacy compatibility runtime is updated immediately, and chat observes the same model state.
- Drag begins only from the handle. The dragged card follows the gesture with translation, scale and elevation; the list remains scrollable outside the handle and auto-scrolls for long drags.
- Provider cards retain expanded state while the screen lives, support case-insensitive name/ID search and Favorites-only filtering.

## Markdown and provider output

Assistant prose uses Markwon 4.6.2 with CommonMark and strikethrough support. Fenced code is rendered separately in a monospace horizontally scrollable surface with a copy action. Provider wrapper objects prefer final `content`/`final`/`answer`/`output_text`; `<think>` and `<analysis>` blocks are removed from user-visible bubbles.

## Floating Assistant

The floating assistant reuses the project's proven EasyFloat Android overlay. A user-enabled foreground service owns its lifetime. It requests overlay access only from its Settings page, persists enable state, position, opacity, size and edge-snap preferences, and shares recent messages/send/stop callbacks with `ComposeChatActivity` through `SharedChatBus`.

## Kai MCP mapping

| Kai 9000 | OctoBot | Adaptation |
|---|---|---|
| `mcp/PopularMcpServers.kt` | `mcp/KaiPopularMcpServers.kt` | Apache-2.0 preset list copied and adapted to Android package names. |
| `mcp/McpServerConfig.kt` | `mcp/McpManager.kt` (`McpServer`) | URL, enabled state, bearer authentication and arbitrary headers. |
| `mcp/McpClient.kt` / `McpServerManager.kt` | `mcp/McpManager.kt` | Initialize, tools/list, tools/call, status and persisted server lifecycle. |
| `mcp/McpTool.kt` | `mcp/McpManager.kt` (`McpProxyTool`) | Remote schemas become real `BaseTool` entries. |
| `ui/settings/McpSection.kt` | `ui/settings/AgentFeaturesActivity.kt` (`McpScreen`) | Suggested/custom review, connect test, enable, edit and delete in Siko's design system. |

Disabling or deleting an MCP server now unregisters its runtime tool prefix; refreshing replaces tools by stable registry names rather than duplicating them.

## License

The visible Settings attribution row was removed. Kai attribution remains in packaged license assets and project notices, as required by Apache-2.0.
