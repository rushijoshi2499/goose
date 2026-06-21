package com.goose.app.bridge

/**
 * JNI bridge to the Rust goose_core library.
 *
 * Mirrors GooseRustBridge.swift: JSON-RPC envelope pattern.
 * Request: {"schema":"goose.bridge.request.v1","method":"...","args":{...}}
 * Response: {"ok":true,"result":...,"error":null,"timing":...}
 *
 * THREADING: handle() blocks the calling thread for the full Rust+SQLite
 * round trip. Never call from the main thread. Use safeHandle() for
 * error-safe invocation, or wrap in a coroutine on Dispatchers.IO.
 */
object GooseBridge {
    init {
        System.loadLibrary("goose_core")
    }

    /**
     * Raw JNI call to Rust goose_bridge_handle_json via android_jni.rs.
     * Throws UnsatisfiedLinkError if .so is not loaded, or any Throwable
     * from the native side.
     */
    external fun handle(request: String): String

    /**
     * Safe wrapper — returns error JSON instead of throwing.
     * Use this in all production code paths.
     */
    fun safeHandle(request: String): String {
        return try {
            handle(request)
        } catch (e: Throwable) {
            buildErrorJson(e.message ?: "Unknown native error")
        }
    }

    private fun buildErrorJson(message: String): String {
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"ok":false,"result":null,"error":{"message":"$escaped"},"timing":null}"""
    }
}
