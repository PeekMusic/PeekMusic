with open("app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt", "r") as f:
    content = f.read()

target1 = """    private suspend fun buildRadioStartPosition(song: Song): MediaItemsWithStartPosition? {
        val radioQueue = YouTubeQueue.radio(song.toMediaMetadata())"""
replacement1 = """    private suspend fun buildRadioStartPosition(metadata: com.metrolist.music.models.MediaMetadata): MediaItemsWithStartPosition? {
        val radioQueue = YouTubeQueue.radio(metadata)"""

target2 = """            radioStatus.items.indexOfFirst { it.mediaId == song.id }.coerceAtLeast(0),"""
replacement2 = """            radioStatus.items.indexOfFirst { it.mediaId == metadata.id }.coerceAtLeast(0),"""

target3 = """                    if (context.dataStore.get(AutoRadioQueueKey, true)) {
                        database.song(songId).first()?.let { selectedSong ->
                            buildRadioStartPosition(selectedSong)?.let { return@future it }
                        }
                    }"""
replacement3 = """                    if (context.dataStore.get(AutoRadioQueueKey, true)) {
                        database.song(songId).first()?.let { selectedSong ->
                            buildRadioStartPosition(selectedSong.toMediaMetadata())?.let { return@future it }
                        }
                    }"""

target4 = """                MusicService.YT_SONG -> {
                    val songId = path.getOrNull(1) ?: return@future defaultResult
                    val mediaId = "${MusicService.YT_SONG}/$songId"
                    if (ytSongMediaItems.isEmpty()) {
                        runCatching { loadYouTubeHomeItems() }
                            .getOrNull()
                            ?.filterIsInstance<SongItem>()
                            ?.take(100)
                            ?.forEach { ytSongMediaItem(it) }
                    }
                    val songs = ytSongMediaItems.values.toList()
                    val index = songs.indexOfFirst { it.mediaId == mediaId }
                    if (songs.isEmpty() || index == -1) {
                        return@future defaultResult
                    }
                    MediaItemsWithStartPosition(songs, index, C.TIME_UNSET)
                }"""
replacement4 = """                MusicService.YT_SONG -> {
                    val songId = path.getOrNull(1) ?: return@future defaultResult
                    val mediaId = "${MusicService.YT_SONG}/$songId"
                    if (ytSongMediaItems.isEmpty()) {
                        runCatching { loadYouTubeHomeItems() }
                            .getOrNull()
                            ?.filterIsInstance<SongItem>()
                            ?.take(100)
                            ?.forEach { ytSongMediaItem(it) }
                    }

                    if (context.dataStore.get(AutoRadioQueueKey, true)) {
                        ytSongMediaItems[mediaId]?.let { selectedItem ->
                            val meta = com.metrolist.music.models.MediaMetadata(
                                id = songId,
                                title = selectedItem.mediaMetadata.title.toString(),
                                artists = listOf(com.metrolist.music.models.Artist(id = null, name = selectedItem.mediaMetadata.artist.toString())),
                                duration = -1,
                                thumbnail = selectedItem.mediaMetadata.artworkUri?.toString() ?: "",
                            )
                            buildRadioStartPosition(meta)?.let { return@future it }
                        }
                    }

                    val songs = ytSongMediaItems.values.toList()
                    val index = songs.indexOfFirst { it.mediaId == mediaId }
                    if (songs.isEmpty() || index == -1) {
                        return@future defaultResult
                    }
                    MediaItemsWithStartPosition(songs, index, C.TIME_UNSET)
                }"""

content = content.replace(target1, replacement1).replace(target2, replacement2).replace(target3, replacement3).replace(target4, replacement4)

with open("app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt", "w") as f:
    f.write(content)
