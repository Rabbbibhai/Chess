package com.saucyfy.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false
)

val Profile.shownName: String get() = displayName?.takeIf { it.isNotBlank() } ?: username

@Serializable
data class Friendship(
    val id: String,
    val requester: String,
    val addressee: String,
    val status: String
)

/** One row in the Friends tab: a person plus how they relate to you. */
data class FriendEntry(
    val friendshipId: String,
    val profile: Profile,
    val accepted: Boolean,
    val incoming: Boolean // true = they asked you
)

/** A published app version + its "what's new" notes (one change per line). */
@Serializable
data class AppUpdate(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    val notes: String,
    @SerialName("apk_url") val apkUrl: String,
    val mandatory: Boolean = false
)

/** Edit distance between two strings. */
fun lev(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev[b.length]
}

/** Closest known title to a (probably misspelled) query, or null if nothing is close. */
fun closestTitle(query: String, pool: List<String>): String? {
    val q = query.trim().lowercase()
    if (q.length < 3) return null
    val limit = when {
        q.length <= 4 -> 1
        q.length <= 8 -> 2
        else -> 3
    }
    var best: String? = null
    var bestD = Int.MAX_VALUE
    for (t in pool.distinct()) {
        val tl = t.lowercase()
        if (tl == q) return null
        val d = minOf(lev(q, tl), lev(q, tl.take(q.length)))
        if (d in 1..limit && d < bestD) {
            best = t
            bestD = d
        }
    }
    return best
}
