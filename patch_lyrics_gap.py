with open("app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt", "r") as f:
    content = f.read()

target = """    if (isInGap) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding)
                .clickable(onClick = onShowLyrics),
            contentAlignment = Alignment.Center,
        ) {
            IntervalIndicator(
                gapStartMs = gapRange!!.first,
                gapEndMs = gapRange.second - 650L,
                currentPositionMs = position + offset,
                visible = true,
                color = contentColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    val currentLine = activeLine ?: return"""

replacement = """    if (isInGap) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding)
                .clickable(onClick = onShowLyrics),
            contentAlignment = Alignment.Center,
        ) {
            IntervalIndicator(
                gapStartMs = gapRange!!.first,
                gapEndMs = gapRange.second - 650L,
                currentPositionMs = position + offset,
                visible = true,
                color = contentColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    val currentLine = if (gapRange != null && position + offset >= gapRange.second - 650L && position + offset < gapRange.second) {
        if (activeLine == null) {
            syncedEntries.firstOrNull()
        } else {
            syncedEntries.getOrNull(syncedEntries.indexOf(activeLine) + 1)
        }
    } else {
        activeLine
    } ?: return"""

content = content.replace(target, replacement, 1)

with open("app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt", "w") as f:
    f.write(content)
