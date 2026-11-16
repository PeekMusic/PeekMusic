package com.metrolist.shared.peek

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeekCaptionClientsTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val noCaptions = json("""{"playabilityStatus":{"status":"UNPLAYABLE"}}""")
    private val withCaptions =
        json("""{"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[{"baseUrl":"x"}]}}}""")

    @Test
    fun detectsCaptionTracks() {
        assertTrue(withCaptions.hasCaptionTracks())
        assertFalse(noCaptions.hasCaptionTracks())
        assertFalse(json("""{"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[]}}}""").hasCaptionTracks())
    }

    @Test
    fun fallsBackToNextClientWhenFirstHasNoCaptions() =
        runTest {
            val asked = mutableListOf<String>()
            val result =
                peekPlayerWithCaptions(listOf("web", "ios")) { client ->
                    asked += client
                    if (client == "web") noCaptions else withCaptions
                }
            assertEquals(withCaptions, result)
            assertEquals(listOf("web", "ios"), asked)
        }

    @Test
    fun stopsAtFirstClientWithCaptions() =
        runTest {
            val asked = mutableListOf<String>()
            peekPlayerWithCaptions(listOf("web", "ios")) { client ->
                asked += client
                withCaptions
            }
            assertEquals(listOf("web"), asked)
        }

    @Test
    fun skipsFailingClientAndFailsOnlyWhenAllFail() =
        runTest {
            val result =
                peekPlayerWithCaptions(listOf("web", "ios")) { client ->
                    if (client == "web") error("boom") else noCaptions
                }
            assertEquals(noCaptions, result)
            assertFailsWith<IllegalStateException> {
                peekPlayerWithCaptions(listOf("web", "ios")) { error("boom") }
            }
        }
}
