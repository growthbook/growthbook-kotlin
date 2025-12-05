package com.sdk.growthbook.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents the category of errors that can occur during the SSE (Server-Sent Events)
 * streaming process.
 *
 * These values help distinguish between network-related issues, server-side failures,
 * connection lifecycle interruptions, and unexpected exceptions.
 */
enum class SSEErrorType {
    NETWORK_UNAVAILABLE,
    SERVER_ERROR,
    TIMEOUT,
    CONNECTION_CLOSED,
    SOCKET_CLOSED,
    UNKNOWN
}

/**
 * Represents the current lifecycle state of the SSE connection.
 *
 * This is used internally by the SDK to control whether incoming events should be
 * processed, temporarily ignored, or if the connection should be fully terminated.
 */
enum class SSEConnectionState {
    /** SSE connection is active and events are being streamed. */
    ACTIVE,

    /**
     * The SSE connection is permanently stopped.
     * Once stopped, the connection cannot be resumed and must be restarted manually.
     */
    STOPPED
}

/**
 * Controller responsible for managing the lifecycle state of the SSE connection.
 *
 * The SDK uses this controller to start and stop event streaming.
 * Consumers (app-side code) can observe changes through [connectionState] and update
 * the UI or internal logic accordingly.
 *
 * State transitions:
 * - `resume()` → switches back to [SSEConnectionState.ACTIVE]
 * - `stop()` → switches to [SSEConnectionState.STOPPED] (terminal state)
 */
class SSEConnectionController {
    private val _connectionState = MutableStateFlow(SSEConnectionState.STOPPED)

    /**
     * A hot observable stream representing the current connection state.
     * Emits updates whenever the state is changed via pause/resume/stop.
     */
    val connectionState: StateFlow<SSEConnectionState> = _connectionState.asStateFlow()

    /**
     * Starts the SSE connection. If already active, does nothing.
     */
    fun start() {
        if (_connectionState.value != SSEConnectionState.ACTIVE) {
            _connectionState.value = SSEConnectionState.ACTIVE
        }
    }

    /**
     * Permanently stops the SSE connection.
     * This is a terminal state; to reconnect, the connection must be started again.
     */
    fun stop() {
        _connectionState.value = SSEConnectionState.STOPPED
    }

    /** Returns true if the connection is currently active. */
    fun isActive(): Boolean = _connectionState.value == SSEConnectionState.ACTIVE

    /** Returns true if the connection is permanently stopped. */
    fun isStopped(): Boolean = _connectionState.value == SSEConnectionState.STOPPED
}
