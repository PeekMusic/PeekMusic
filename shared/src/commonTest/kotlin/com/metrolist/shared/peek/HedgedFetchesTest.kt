/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HedgedFetchesTest {
    @Test
    fun fastFetchesRunOneAtATime() =
        runTest {
            val started = mutableListOf<Int>()
            coroutineScope {
                val fetches =
                    HedgedFetches(this, 3, hedgeDelayMs = 500) { index ->
                        started += index
                        delay(100)
                        "result $index"
                    }
                assertEquals("result 0", fetches.await(0))
                assertEquals(listOf(0), started)
                assertEquals("result 1", fetches.await(1))
                assertEquals(listOf(0, 1), started)
                fetches.cancelRemaining()
            }
        }

    @Test
    fun slowFetchStartsTheNextAfterTheHedgeDelay() =
        runTest {
            val startedAt = mutableMapOf<Int, Long>()
            coroutineScope {
                val fetches =
                    HedgedFetches(this, 3, hedgeDelayMs = 500) { index ->
                        startedAt[index] = currentTime
                        delay(if (index == 0) 10_000 else 200)
                        "result $index"
                    }
                assertEquals("result 0", fetches.await(0))
                assertEquals(0L, startedAt[0])
                assertEquals(500L, startedAt[1])
                assertEquals(1_000L, startedAt[2])
                assertEquals("result 1", fetches.await(1))
                assertEquals(10_000L, currentTime)
                fetches.cancelRemaining()
            }
        }

    @Test
    fun finishedLowerPriorityResultsAreReportedWhileWaiting() =
        runTest {
            val reported = mutableListOf<Pair<Int, Long>>()
            coroutineScope {
                val fetches =
                    HedgedFetches(this, 2, hedgeDelayMs = 500) { index ->
                        delay(if (index == 0) 5_000 else 100)
                        "result $index"
                    }
                assertEquals(
                    "result 0",
                    fetches.await(0) { index, _ ->
                        reported += index to currentTime
                        false
                    },
                )
                assertEquals(listOf(1 to 1_000L), reported)
                fetches.cancelRemaining()
            }
        }

    @Test
    fun cancelRemainingStopsFetchesStartedAhead() =
        runTest {
            val finished = mutableListOf<Int>()
            coroutineScope {
                val fetches =
                    HedgedFetches(this, 2, hedgeDelayMs = 500) { index ->
                        delay(if (index == 0) 600 else 10_000)
                        finished += index
                        "result $index"
                    }
                fetches.await(0)
                fetches.cancelRemaining()
            }
            assertEquals(listOf(0), finished)
        }
}
