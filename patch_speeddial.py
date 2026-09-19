with open("app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt", "r") as f:
    content = f.read()

target = """    val mixed = mutableListOf<YTItem>()
    val iterators = listOf(quickPicks.iterator(), home.iterator(), keepListening.iterator())
    
    var added = true
    while (added && mixed.size < availableSpace * 2) { // Allow buffer for distinctBy
        added = false
        for (it in iterators) {
            if (it.hasNext()) {
                mixed.add(it.next())
                added = true
            }
        }
    }"""

replacement = """    val mixed = mutableListOf<YTItem>()
    val iterKeep = keepListening.iterator()
    val iterHome = home.iterator()
    val iterQuick = quickPicks.iterator()
    
    // Weight: 2 keepListening, 2 home, 1 quickPicks
    val iterators = listOf(iterKeep, iterKeep, iterHome, iterHome, iterQuick)
    
    var added = true
    while (added && mixed.size < availableSpace * 2) { // Allow buffer for distinctBy
        added = false
        for (it in iterators) {
            if (it.hasNext()) {
                mixed.add(it.next())
                added = true
            }
        }
    }"""

content = content.replace(target, replacement, 1)

with open("app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt", "w") as f:
    f.write(content)
