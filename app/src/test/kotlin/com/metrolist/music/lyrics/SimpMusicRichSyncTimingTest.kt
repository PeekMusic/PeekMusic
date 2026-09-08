package com.metrolist.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SimpMusic richSyncLyrics (Enhanced/Word-LRC) sample lines, taken verbatim from
 * docs/benchmarks/simpmusic-debug/ (KAI "Bomba" = XnTti1hXbH0, EXO "Ooh La La La"
 * = 8l4OGw3bEto). In this format the last word of a line has no `<e>` end tag;
 * the trailing `<t>` tag is the line-end marker and usually repeats the last
 * word's start time (0 ms duration).
 *
 * Expected semantics (matching the official SimpMusic parser): a word ends where
 * the next word starts (whitespace-only tags are end markers, not word starts),
 * and the last word ends at the trailing marker only when it lies after the
 * word, otherwise at the next line's start.
 */
@RunWith(RobolectricTestRunner::class)
class SimpMusicRichSyncTimingTest {

    private val bombaLines = """
        [00:05.38] <00:05.38>Bomba<00:06.03> <00:06.16>bomba <00:06.16>
        [00:11.51] <00:11.51>No<00:11.64> <00:11.78>matter<00:11.92> <00:12.04>no<00:12.16> <00:12.29>es<00:12.43> <00:12.48>nada <00:12.53>
        [00:13.97] <00:13.97>낮보다<00:14.48> <00:14.57>밝은<00:14.99> <00:15.03>이<00:15.08> <00:15.08>밤 <00:15.08>
        [00:16.52] <00:16.52>자유롭게<00:17.77> <00:17.91>더<00:18.19> <00:18.24>뜨겁게 <00:18.29>
    """.trimIndent()

    private val oohLaLaLaChorus = """
        [00:46.23] <00:46.23>(Ooh-<00:46.41>la-<00:46.56>la-<00:46.79>la)<00:47.79> <00:48.09>나를<00:48.76> <00:48.94>허락해줘요 <00:50.99>
        [00:51.86] <00:51.86>(Ooh-<00:52.02>la-<00:52.15>la-<00:52.54>la)<00:54.14> <00:54.25>너의<00:54.62> <00:54.92>상상<00:55.24> <00:55.52>속으로 <00:56.94>
    """.trimIndent()

    @Test
    fun `word end equals next word start, not the word's own end tag`() {
        val parsed = LyricsUtils.parseLyrics(bombaLines)
        val line = parsed[1] // "No matter no es nada"
        val words = line.words!!

        assertEquals("No", words[0].text)
        // Old bug: "No" ended at its <e> tag (11.64); it must run gapless to the
        // next word start (11.78).
        assertEquals(11.78, words[0].endTime, 0.001)
        for (i in 0 until words.size - 1) {
            assertEquals(
                "word ${words[i].text} must end where ${words[i + 1].text} starts",
                words[i + 1].startTime,
                words[i].endTime,
                0.001,
            )
        }
    }

    @Test
    fun `last word with zero-duration trailing marker runs to next line start`() {
        val parsed = LyricsUtils.parseLyrics(bombaLines)
        val line = parsed[2] // "낮보다 밝은 이 밤", trailing <00:15.08> == last word start
        val lastWord = line.words!!.last()

        assertEquals("밤", lastWord.text)
        assertEquals(15.08, lastWord.startTime, 0.001)
        // Must not be the 0 ms flash the trailing marker would give; the next
        // line starts at 16.52.
        assertTrue(lastWord.endTime > lastWord.startTime)
        assertEquals(16.52, lastWord.endTime, 0.001)
    }

    @Test
    fun `last word with real trailing marker duration uses it`() {
        val parsed = LyricsUtils.parseLyrics(oohLaLaLaChorus)
        val line = parsed[0] // "…허락해줘요", trailing <00:50.99> is 2.05 s after the word start
        val lastWord = line.words!!.last()

        assertEquals("허락해줘요", lastWord.text)
        assertEquals(48.94, lastWord.startTime, 0.001)
        assertEquals(50.99, lastWord.endTime, 0.001)
        // and it must not overshoot into the next line (starts 51.86)
        assertTrue(lastWord.endTime <= 51.86)
    }

    @Test
    fun `line stays active until the next line starts`() {
        val parsed = LyricsUtils.parseLyrics(bombaLines)
        // 7.0 s: last word of line 0 already ended (trailing marker 6.16), but
        // the next line does not start until 11.51 — line 0 must still be active.
        val activeAt7s = LyricsUtils.findActiveLineIndices(parsed, 7000L)
        assertTrue(activeAt7s.contains(0))

        // 50.5 s in the chorus: 허락해줘요 is still singing (ends 50.99).
        val chorus = LyricsUtils.parseLyrics(oohLaLaLaChorus)
        val activeAt50s = LyricsUtils.findActiveLineIndices(chorus, 50500L)
        assertTrue(activeAt50s.contains(0))
    }
}
