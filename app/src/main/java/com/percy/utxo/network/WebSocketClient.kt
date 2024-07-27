package com.percy.utxo.network

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WebSocketClient {
    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }

    private val incomingMessages = Channel<String>(Channel.UNLIMITED)

    fun getIncomingMessages() = incomingMessages

    fun connect() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                runCatching {
                    client.wss(
                        method = HttpMethod.Get,
                        host = "fstream.binance.com",
                        path = "/ws/btcusdt@markPrice",
                        request = {
                            header(HttpHeaders.ContentType, ContentType.Application.Json)
                        }
                    ) {
                        val job = launch {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val receivedText = frame.readText()
                                        println("Received: $receivedText")
                                        incomingMessages.send(receivedText)
                                    }
                                    is Frame.Ping -> {
                                        println("Received Ping, sending Pong")
                                        send(Frame.Pong(frame.data))
                                    }
                                    is Frame.Pong -> {
                                        println("Received Pong")
                                    }
                                    is Frame.Close -> {
                                        println("Received Close frame: ${frame.readReason()}")
                                        break
                                    }
                                    else -> {
                                        println("Received other frame: $frame")
                                    }
                                }
                            }
                        }
                        job.join()
                    }
                }.getOrElse {
                    println("Error during WebSocket communication: ${it.message}")
                    delay(5000) // Delay before reconnecting
                }
            }
        }
    }

    fun close() {
        client.close()
    }
}
