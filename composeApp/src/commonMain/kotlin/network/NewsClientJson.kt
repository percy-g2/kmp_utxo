package network

import kotlinx.serialization.json.Json

/**
 * Shared JSON config for the news/AI HTTP client returned by `createNewsHttpClient()` on every
 * platform. `ignoreUnknownKeys` lets the OpenAI-compatible AI responses (which carry extra top-level
 * keys such as `id`, `object`, `created`, `model`, `usage`, …) deserialize into our slim DTOs.
 *
 * Kept in one place so the config can't drift apart across the platform `actual`s again — the
 * previous iOS/Desktop/WasmJS actuals used the bare `json()` default (`ignoreUnknownKeys = false`)
 * and crashed on the provider's `id` key while Android worked.
 */
internal val newsClientJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
}
