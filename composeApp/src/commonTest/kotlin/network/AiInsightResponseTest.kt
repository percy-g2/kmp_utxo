package network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.decodeFromString
import model.ChatCompletionResponse

/**
 * Regression tests for deserializing the OpenAI-compatible AI response through [newsClientJson] —
 * the exact JSON config the news/AI HTTP client uses on every platform.
 *
 * Pollinations returns extra top-level keys (`id`, `object`, `created`, `model`, `usage`, …) that
 * our slim [ChatCompletionResponse] DTO does not model. Before the fix the iOS/Desktop/WasmJS
 * clients used the bare `json()` default and threw `unknown key 'id'`; these lock in that the shared
 * config tolerates those keys and still extracts the message content.
 */
class AiInsightResponseTest {

    @Test
    fun response_with_extra_provider_keys_still_parses() {
        val sample = """
            {"id":"pllns_abc","object":"chat.completion","created":1730000000,"model":"openai",
             "choices":[{"index":0,"finish_reason":"stop",
               "message":{"role":"assistant","content":"BTC momentum looks constructive."}}],
             "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
        """.trimIndent()

        val parsed = newsClientJson.decodeFromString<ChatCompletionResponse>(sample)

        assertEquals("BTC momentum looks constructive.", parsed.choices.firstOrNull()?.message?.content)
    }

    @Test
    fun choice_with_extra_keys_still_parses() {
        // Unknown keys nested inside a choice (e.g. `logprobs`) must not break parsing either.
        val sample = """
            {"id":"pllns_def","choices":[{"index":0,"logprobs":null,"finish_reason":"stop",
               "message":{"role":"assistant","content":"Volatility is elevated.","refusal":null}}]}
        """.trimIndent()

        val parsed = newsClientJson.decodeFromString<ChatCompletionResponse>(sample)

        assertEquals("Volatility is elevated.", parsed.choices.firstOrNull()?.message?.content)
    }
}
