package network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiCompletionGatewayTest {

    @Test
    fun model_unavailable_error_triggers_fallback() {
        val body = """
            {"error":{"message":"Model is unavailable","code":"model_unavailable"}}
        """.trimIndent()

        assertTrue(isModelUnavailableError(body))
    }

    @Test
    fun unrelated_provider_error_does_not_trigger_fallback() {
        val body = """
            {"error":{"message":"Rate limit exceeded","code":"rate_limit_exceeded"}}
        """.trimIndent()

        assertFalse(isModelUnavailableError(body))
    }

    @Test
    fun malformed_error_does_not_trigger_fallback() {
        assertFalse(isModelUnavailableError("not json"))
    }
}
