with open("app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt", "r") as f:
    content = f.read()

target = """            initialStatus = initialStatus.copy(items = cleanItems, mediaItemIndex = 0.coerceAtMost(cleanItems.lastIndex.coerceAtLeast(0)))"""

replacement = """            initialStatus = initialStatus.copy(items = cleanItems, mediaItemIndex = initialStatus.mediaItemIndex.coerceIn(0, cleanItems.lastIndex.coerceAtLeast(0)))"""

content = content.replace(target, replacement, 1)

with open("app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt", "w") as f:
    f.write(content)
