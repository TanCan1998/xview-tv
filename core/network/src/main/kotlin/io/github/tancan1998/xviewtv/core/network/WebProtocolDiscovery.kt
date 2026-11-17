package io.github.tancan1998.xviewtv.core.network

/** Parse data only, never evaluate executable web code. Resolve current hashed timeline bundles. */
object WebProtocolDiscovery {
    fun timelineChunks(html: String): List<String> = operationChunks(html, "HomeLatestTimeline")

    fun operationChunks(html: String, operation: String): List<String> {
        val bundles = when (operation) {
            "HomeLatestTimeline" -> listOf("HomeTimeline")
            "SearchTimeline" -> listOf("SearchTimeline", "Search")
            "Following" -> listOf("UserLists", "Following", "Profile")
            "Likes", "UserMedia", "UserTweets" -> listOf("Profile", "UserProfile")
            else -> emptyList()
        }
        val pairs = Regex("([0-9]{1,6}):\"([^\"]{1,240})\"").findAll(html).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val hashes = pairs.filter { it.second.matches(Regex("[a-f0-9]{16,20}")) }.toMap()
        return pairs.filter { (_, name) -> bundles.any { name.contains(it) } && !name.startsWith("loader.") }
            .mapNotNull { (id, name) ->
                val hash = hashes[id] ?: return@mapNotNull null
                if (!name.matches(Regex("[A-Za-z0-9_.~-]+"))) return@mapNotNull null
                "https://abs.twimg.com/responsive-web/client-web/$name.${hash}a.js"
            }.distinct().sortedBy { if (it.contains("shared~bundle")) 0 else 1 }.take(6)
    }
}
