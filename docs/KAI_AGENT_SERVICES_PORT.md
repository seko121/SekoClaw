# Kai 9000 Agent and Services port

Source project: `kai-reference` (Apache License 2.0). Copyright notices are retained in adapted source files, and the Kai license/notice is packaged under `app/src/main/assets/licenses/`.

## File mapping

| Kai 9000 source | OctoBot destination | Adaptation |
|---|---|---|
| `data/Service.kt` | `agent/llm/kai/KaiServiceRegistry.kt` | Provider catalog, endpoint metadata, protocols, defaults; Compose resource references replaced by Android-neutral metadata. |
| `network/Requests.kt` | `agent/llm/kai/KaiProviderGateway.kt` | OpenAI-compatible, Anthropic and Gemini authentication, model discovery, connection tests, request formats and friendly errors adapted to OkHttp. |
| `network/dtos/gemini/*` | `agent/llm/GeminiLlmClient.kt` | Gemini messages, system instruction, function declarations/calls/results and response parsing adapted to the existing `LlmClient`. |
| `ui/settings/ServicesSettings.kt` | `ui/settings/ProviderManagementActivity.kt` | Services, Local Models and Routing tabs connected to Siko's design system and runtime stores. |
| `ui/settings/ModelSelectionSheet.kt` | `ui/chat/ChatScreen.kt` | Replaced visible Local/Cloud controls with one Models entry and live active-model status. |
| `inference/LocalModelCatalog.kt` | `agent/llm/LocalModelManager.kt` | Added Gemma 4 12B and Qwen3 0.6B with Kai URLs, sizes and context metadata. |
| `inference/LocalModelCatalog.kt` (`stripThinkBlocks`) | `agent/llm/LocalLlmClient.kt` | Removes Qwen reasoning tags before rendering. |
| `data/AppSettings.kt` service instances | `agent/llm/MultiProviderStore.kt` | Multiple named provider/model configs, active model, routing and non-destructive legacy migration. |
| Kai credential storage contract | `agent/llm/MultiProviderStore.kt` (`SecureSecretStore`) | API keys encrypted with Android Keystore AES/GCM and stored separately from provider JSON. |
| `data/MemoryStore.kt` | `agent/memory/KaiMemoryStore.kt` | Structured keyed memories, categories, source, update time and enable state using Siko persistence. |
| `tools/HeartbeatTools.kt` memory updates | `agent/memory/AgentMemoryTool.kt` | Agent writes structured memories only while Memories is enabled. |
| `data/ChatSystemPromptBuilder.kt` | `agent/PromptUtils.kt` | Soul, enabled memories, tools and skills are injected into the actual runtime system prompt. |
| `ui/settings/AgentSettings.kt` | `ui/settings/AgentFeaturesActivity.kt` | Unified Agent page with Soul, memory management, scheduled tasks, heartbeat and device services. |
| `data/HeartbeatManager.kt`, `HeartbeatPromptBuilder.kt` | `heartbeat/HeartbeatManager.kt`, `cron/CronManager.kt` | Persisted enable/interval/active-hours/prompt, runtime active-hours enforcement, last run/result and scheduled execution. |
| `sms/SmsReader.android.kt`, `tools/SmsTools.kt` | `tool/impl/SmsTools.kt` | Permission-gated inbox search/read plus user-confirmed SMS draft handoff. |
| `notifications/NotificationReader.kt` | `tool/impl/GetNotificationsTool.java`, `agent/services/AgentDeviceServices.kt`, `ui/settings/AgentFeaturesActivity.kt` | Notification access obeys the Agent service switch and the persisted per-app allowlist. |

## Runtime integration

- `ModelConfigRepository` resolves the persisted active Services model for chat, agent tasks, scheduled tasks and heartbeat.
- `LlmClientFactory` routes OpenAI-compatible, Anthropic, Gemini and LiteRT models to their real protocol clients.
- `TaskOrchestrator` follows the persisted routing order and falls back only for transient provider/network failures before tools have executed.
- Custom provider headers are injected by `OkHttpClientBuilderAdapter`; request bodies and API keys are not logged by default.
- Legacy cloud settings are migrated once. Provider/model/base URL metadata is backed up temporarily; the migrated API key is moved to Keystore-backed storage and cleared from legacy plaintext storage.

## Verification record

- Debug Kotlin compilation: passed.
- JVM unit tests: passed, including transient/terminal fallback rules and Kai provider catalog/error mapping.
- Debug APK assembly: passed.
- Emulator install: passed on `emulator-5554`.
- Visual checks: main Models entry, Terminal shortcut, Services empty state, provider catalog dialog and local model catalog.
- Runtime provider calls require user-owned API credentials and therefore are verified through request-format tests and in-app Test connection, not claimed as live-account tests.

## Deliberate verification boundaries

- No user API keys are bundled or printed. Live paid-provider calls must be completed with the account owner's key through **Test connection**.
- Multi-gigabyte local models are checked for catalog metadata, storage gating, download state and runtime routing; a complete model download is not claimed in this development build.
- Kai's raw-password IMAP/SMTP implementation has not been exposed as a partial UI. Email remains unavailable until its complete encrypted-account and user-approval flow is ported.
