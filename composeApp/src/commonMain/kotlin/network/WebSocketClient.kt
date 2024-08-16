package network

import getWebSocketClient
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.header
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WebSocketClient {
    private val client = getWebSocketClient()

    private var job: Job? = null

    private val incomingMessages = Channel<String>(Channel.UNLIMITED)

    fun getIncomingMessages(): Flow<String> = incomingMessages.receiveAsFlow()

    fun connect() {
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                runCatching {
                    client.wss(
                        method = HttpMethod.Get,
                        host = "stream.binance.com",
                        path = "/ws/!ticker@arr",
                        request = {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                        }
                    ) {
                        val job = launch {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val receivedText = frame.readText()
                                        incomingMessages.send(receivedText)
                                    }
                                    is Frame.Ping -> {
                                        send(Frame.Pong(frame.data))
                                    }
                                    is Frame.Pong -> {
                                        // no op
                                    }
                                    is Frame.Close -> {
                                        break
                                    }
                                    else -> {
                                        // no op
                                    }
                                }
                            }
                        }
                        job.join()
                    }
                }.getOrElse {
                    it.printStackTrace()
                    delay(5000) // Delay before reconnecting
                }
            }
        }
    }

    fun close() {
        if (job?.isActive == true) {
            job?.cancel()
        }
        client.close()
    }
}
