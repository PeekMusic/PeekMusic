import re

with open("app/src/main/kotlin/com/metrolist/music/ui/screens/settings/LyricsSettings.kt", "r") as f:
    content = f.read()

# Remove import
content = re.sub(r"import com\.metrolist\.music\.constants\.EnableMusixmatchKey\n", "", content)

# Remove preference state
content = re.sub(r" *val \(enableMusixmatch, onEnableMusixmatchChange\) = rememberPreference\(key = EnableMusixmatchKey, defaultValue = true\)\n", "", content)

# Remove map entry
content = re.sub(r' *"Musixmatch" to "Musixmatch",\n', "", content)

# Remove PreferenceEntry block for Musixmatch
pref_entry_regex = r""" *PreferenceEntry\(\n *title = \{\n *Text\(stringResource\(R\.string\.enable_musixmatch\)\)\n *\},\n *description = \{\n *Text\(\n *text = stringResource\(R\.string\.enable_musixmatch_desc\),\n *style = MaterialTheme\.typography\.bodyMedium\n *\)\n *\},\n *trailingContent = \{\n *Switch\(\n *checked = enableMusixmatch,\n *onCheckedChange = onEnableMusixmatchChange,\n *thumbContent = \{\n *Icon\(\n *painter = painterResource\(\n *id = if \(enableMusixmatch\) R\.drawable\.check else R\.drawable\.close\n *\),\n *contentDescription = null,\n *modifier = Modifier\.size\(SwitchDefaults\.IconSize\)\n *\)\n *\}\n *\)\n *\},\n *onClick = \{ onEnableMusixmatchChange\(!enableMusixmatch\) \}\n *\)\n"""
content = re.sub(pref_entry_regex, "", content, flags=re.MULTILINE)

# Remove from manual order list
content = re.sub(r' *"Musixmatch"\.takeIf \{ enableMusixmatch \},\n', "", content)

# Remove from LaunchedEffect
content = re.sub(r"enableMusixmatch, ", "", content)

with open("app/src/main/kotlin/com/metrolist/music/ui/screens/settings/LyricsSettings.kt", "w") as f:
    f.write(content)
