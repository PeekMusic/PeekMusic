with open("app/src/main/kotlin/com/metrolist/music/ui/screens/settings/LyricsSettings.kt", "r") as f:
    content = f.read()

import re

# We will just find the Row that contains enable_musixmatch and delete it
pattern = r" {20}Row\(\n {24}modifier = Modifier\.fillMaxWidth\(\),\n {24}horizontalArrangement = Arrangement\.SpaceBetween,\n {24}verticalAlignment = Alignment\.CenterVertically,\n {20}\) \{\n {24}Column\(\n {28}modifier = Modifier\.weight\(1f\)\n {24}\) \{\n {28}Text\(stringResource\(R\.string\.enable_musixmatch\)\)\n {28}Text\(\n {32}text = stringResource\(R\.string\.enable_musixmatch_desc\),\n {32}style = MaterialTheme\.typography\.bodySmall,\n {32}color = MaterialTheme\.colorScheme\.onSurfaceVariant\n {28}\)\n {24}\}\n {24}Switch\(\n {28}checked = enableMusixmatch,\n {28}onCheckedChange = onEnableMusixmatchChange,\n {28}thumbContent = \{\n {32}Icon\(\n {36}painter = painterResource\(\n {40}id = if \(enableMusixmatch\) R\.drawable\.check else R\.drawable\.close\n {36}\),\n {36}contentDescription = null,\n {36}modifier = Modifier\.size\(SwitchDefaults\.IconSize\)\n {32}\)\n {28}\}\n {24}\)\n {20}\}\n"

content = re.sub(pattern, "", content)

# I also noticed `enableMusixmatch` might still be somewhere. Let's check:
content = re.sub(r", enableMusixmatch", "", content)
content = re.sub(r"enableMusixmatch, ", "", content)

with open("app/src/main/kotlin/com/metrolist/music/ui/screens/settings/LyricsSettings.kt", "w") as f:
    f.write(content)
