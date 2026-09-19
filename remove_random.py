with open("app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt", "r") as f:
    content = f.read()

target1 = "val totalItemsWithRandom = items.size + 1"
replacement1 = "val totalItemsWithRandom = items.size"

import re

target2_regex = r"val isRandomizeSlot = \(globalIndex == items\.size\).*?\} else if \(globalIndex < items\.size\) \{"

replacement2 = """if (globalIndex < items.size) {"""

content = content.replace(target1, replacement1)
content = re.sub(target2_regex, replacement2, content, flags=re.DOTALL)

with open("app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt", "w") as f:
    f.write(content)
