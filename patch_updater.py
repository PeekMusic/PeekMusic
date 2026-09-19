with open("app/src/main/kotlin/com/metrolist/music/utils/Updater.kt", "r") as f:
    content = f.read()

target1 = """                val response = client.get("$GITHUB_API_BASE/releases?per_page=1")
                    .bodyAsText()
                val releases = JSONArray(response)"""

replacement1 = """                val response = client.get("$GITHUB_API_BASE/releases?per_page=1").bodyAsText()
                if (response.trim().startsWith("{")) {
                    val jsonObj = org.json.JSONObject(response)
                    if (jsonObj.has("message")) {
                        val msg = jsonObj.getString("message")
                        if (msg.contains("API rate limit exceeded")) {
                            error("Du hast zu oft nach Updates gesucht. Bitte warte eine Weile, bevor du es erneut versuchst (GitHub API Limit).")
                        } else {
                            error("GitHub API Error: $msg")
                        }
                    }
                }
                val releases = JSONArray(response)"""

target2 = """                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                        .bodyAsText()
                    val json = JSONArray(response)"""

replacement2 = """                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30").bodyAsText()
                    if (response.trim().startsWith("{")) {
                        val jsonObj = org.json.JSONObject(response)
                        if (jsonObj.has("message")) {
                            val msg = jsonObj.getString("message")
                            if (msg.contains("API rate limit exceeded")) {
                                error("Du hast zu oft nach Updates gesucht. Bitte warte eine Weile, bevor du es erneut versuchst (GitHub API Limit).")
                            } else {
                                error("GitHub API Error: $msg")
                            }
                        }
                    }
                    val json = JSONArray(response)"""

content = content.replace(target1, replacement1).replace(target2, replacement2)

with open("app/src/main/kotlin/com/metrolist/music/utils/Updater.kt", "w") as f:
    f.write(content)
