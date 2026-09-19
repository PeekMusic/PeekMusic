with open("app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt", "r") as f:
    content = f.read()

target1 = """                                artists = listOf(com.metrolist.music.models.Artist(id = null, name = selectedItem.mediaMetadata.artist.toString())),
                                duration = -1,
                                thumbnail = selectedItem.mediaMetadata.artworkUri?.toString() ?: "","""
replacement1 = """                                artists = listOf(MediaMetadata.Artist(id = null, name = selectedItem.mediaMetadata.artist.toString())),
                                duration = -1,
                                thumbnailUrl = selectedItem.mediaMetadata.artworkUri?.toString() ?: "","""

target2 = """                    if (context.dataStore.get(AutoRadioQueueKey, true)) {
                        buildRadioStartPosition(selectedSong ?: return@future defaultResult)
                            ?.let { return@future it }
                    }"""
replacement2 = """                    if (context.dataStore.get(AutoRadioQueueKey, true)) {
                        val song = selectedSong ?: return@future defaultResult
                        buildRadioStartPosition(song.toMediaMetadata())
                            ?.let { return@future it }
                    }"""

content = content.replace(target1, replacement1).replace(target2, replacement2)

with open("app/src/main/kotlin/com/metrolist/music/playback/MediaLibrarySessionCallback.kt", "w") as f:
    f.write(content)
