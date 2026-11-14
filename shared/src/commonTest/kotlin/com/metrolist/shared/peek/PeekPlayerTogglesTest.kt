/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import kotlin.test.Test
import kotlin.test.assertEquals

class PeekPlayerTogglesTest {
    @Test
    fun restoresTheSavedArtworkSources() {
        assertEquals(true to false, restoredArtworkMotion("true,false"))
        assertEquals(false to true, restoredArtworkMotion("false,true"))
        assertEquals(true to true, restoredArtworkMotion("true,true"))
    }

    @Test
    fun fallsBackToTheCanvasWhenNothingUsableWasSaved() {
        assertEquals(true to false, restoredArtworkMotion(""))
        assertEquals(true to false, restoredArtworkMotion("false,false"))
        assertEquals(true to false, restoredArtworkMotion("garbage"))
    }
}
