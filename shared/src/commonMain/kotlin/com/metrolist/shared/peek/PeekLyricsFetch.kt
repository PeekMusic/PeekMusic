/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** How long a lyrics provider may run alone before the next one in priority order starts alongside it. */
internal const val PEEK_LYRICS_HEDGE_DELAY_MS = 500L

/**
 * Starts fetches in priority order and awaits them in that order. While the awaited fetch is still running after
 * [hedgeDelayMs], the next unstarted fetch starts alongside it, so one slow provider no longer delays every later one.
 * Fast providers behave exactly like a sequential lookup: nothing else starts before they finish.
 */
internal class HedgedFetches<T : Any>(
    private val scope: CoroutineScope,
    count: Int,
    /** Null keeps upstream's strictly sequential lookup: each fetch only starts once the previous one is consumed. */
    private val hedgeDelayMs: Long? = PEEK_LYRICS_HEDGE_DELAY_MS,
    private val fetch: suspend (index: Int) -> T,
) {
    private val fetches = arrayOfNulls<Deferred<T>>(count)
    private val reportedAhead = BooleanArray(count)
    private var satisfied = false

    private fun start(index: Int): Deferred<T> = fetches[index] ?: scope.async { fetch(index) }.also { fetches[index] = it }

    private fun startNextAfter(index: Int) {
        val next = (index + 1 until fetches.size).firstOrNull { fetches[it] == null } ?: return
        start(next)
    }

    /**
     * Awaits fetch [index]. While waiting, finished fetches further down the order are passed to [onReadyAhead] once,
     * so a usable lower-priority result can be shown before a slow higher-priority provider gives up. Returning true
     * from [onReadyAhead] means that result is good enough: no further fetches start, only higher priorities are awaited.
     */
    suspend fun await(
        index: Int,
        onReadyAhead: suspend (index: Int, result: T) -> Boolean = { _, _ -> false },
    ): T {
        val current = start(index)
        val delayMs = hedgeDelayMs ?: return current.await()
        while (true) {
            withTimeoutOrNull(delayMs.milliseconds) { current.await() }?.let { return it }
            if (current.isCompleted) return current.await()
            for (ahead in index + 1 until fetches.size) {
                val fetch = fetches[ahead] ?: continue
                if (!reportedAhead[ahead] && fetch.isCompleted) {
                    reportedAhead[ahead] = true
                    if (onReadyAhead(ahead, fetch.await())) satisfied = true
                }
            }
            if (!satisfied) startNextAfter(index)
        }
    }

    /** Cancels fetches that were started ahead but are no longer needed. */
    fun cancelRemaining() {
        fetches.forEach { it?.takeUnless(Deferred<T>::isCompleted)?.cancel() }
    }
}
