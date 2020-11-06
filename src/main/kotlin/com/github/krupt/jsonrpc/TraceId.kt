package com.github.krupt.jsonrpc

import kotlin.coroutines.AbstractCoroutineContextElement

data class TraceId(val id: String): AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<TraceId>
}

