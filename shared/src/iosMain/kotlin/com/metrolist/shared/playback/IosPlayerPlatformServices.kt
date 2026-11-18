package com.metrolist.shared.playback

import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.shared.api.artwork.DirectCanvasProvider
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.state.GlobalYouTube
import com.metrolist.shared.ui.settings.IosNativeFiles
import com.metrolist.shared.ui.theme.ThemeColorExtractor
import com.metrolist.shared.ui.theme.getGradientColors
import com.metrolist.shared.utils.artworkColors
import com.metrolist.shared.viewmodels.PlayerArtworkColors
import com.metrolist.shared.viewmodels.PlayerPlatformServices
import com.metrolist.shared.youtube.extraction.models.toContentHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.UIKit.UIViewController

internal class IosPlayerPlatformServices(
    private val presenter: UIViewController,
) : PlayerPlatformServices {
    // PeekMusic: Spotify Canvas, the same lookup as Android and desktop (spotlib is shared now).
    private val canvasProvider by lazy { DirectCanvasProvider(GlobalYouTube.client) }

    override suspend fun extractArtworkColors(
        songId: String,
        thumbnailUrl: String,
    ): PlayerArtworkColors? {
        val context = PlatformContext.INSTANCE
        val request =
            ImageRequest
                .Builder(context)
                .artworkColors(thumbnailUrl)
                .build()
        val bitmap = ((SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image as? BitmapImage)?.bitmap ?: return null
        val seed = withContext(Dispatchers.Default) { ThemeColorExtractor().extractSeedColor(bitmap) }
        return PlayerArtworkColors(dominant = seed, gradient = getGradientColors(seed))
    }

    override fun shareText(text: String) {
        IosNativeFiles.shareText(presenter, text)
    }

    override fun copyText(text: String) {
        platform.UIKit.UIPasteboard.generalPasteboard.string = text
    }

    override suspend fun spotifyCanvas(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        primaryArtist: String?,
    ): String? = canvasProvider.getSpotifyCanvas(title, artist, durationMs, album, primaryArtist)

    // PeekMusic: above 720p YouTube rarely offers an H.264 stream AVPlayer can play, so the Canvas stops there.
    override val maxMusicVideoCanvasHeight: Int = IOS_MAX_CANVAS_HEIGHT

    // AVPlayer only plays a plain video file here: no SABR or HLS for the Canvas video.
    override fun musicVideoCanvasHints(song: SongItem): ContentHints =
        song
            .toContentHints(wantVideo = true)
            .copy(sabrFirst = false)
            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true)
}

internal const val IOS_MAX_CANVAS_HEIGHT = 720
