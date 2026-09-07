package com.metrolist.music.utils

import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test
    fun kmpReleaseNeverMatchesDripMusicAssets() {
        val response =
            """
            {
              "tag_name": "v1.2.3",
              "body": null,
              "published_at": "2026-09-05T12:00:00Z",
              "assets": [{
                "name": "DripMusic-1.2.3-fossRelease.apk",
                "browser_download_url": "https://example.com/DripMusic-1.2.3-fossRelease.apk",
                "size": 42
              }]
            }
            """.trimIndent()

        // DripMusic has no KMP build: release assets never qualify as a KMP update,
        // so the KMP prompt must stay silent and the normal release path handles updates.
        assertNull(Updater.parseKmpRelease(response))
        assertNull(
            Updater.parseKmpRelease(
                response.replace(
                    "DripMusic-1.2.3-fossRelease.apk",
                    "DripMusic.apk",
                ),
            ),
        )
    }
}
