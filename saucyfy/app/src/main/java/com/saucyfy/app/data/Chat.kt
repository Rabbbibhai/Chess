package com.saucyfy.app.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** One chat message. Chat history is stored on the device only. */
@Serializable
data class ChatMsg(val id: String, val mine: Boolean, val body: String, val ts: Long)

/** A message waiting in the server mailbox (deleted as soon as the recipient downloads it). */
@Serializable
data class MailMsg(
    val id: String,
    val sender: String,
    val body: String,
    @SerialName("created_at") val createdAt: String
)

val GifCategories = listOf(
    "happy", "hug", "pat", "wave", "laugh", "smile", "dance", "thumbsup", "nod", "wink",
    "blush", "cry", "pout", "shrug", "facepalm", "baka", "bored", "sleep", "yawn", "highfive"
)

/** A GIF message is stored as "gif:<url>" (about 60 bytes). Only trusted hosts are rendered. */
fun gifUrlOrNull(body: String): String? {
    if (!body.startsWith("gif:")) return null
    val url = body.removePrefix("gif:")
    return url.takeIf { it.startsWith("https://nekos.best/") }
}

class ChatStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "chats").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("elog_chat", Context.MODE_PRIVATE)
    private val ser = ListSerializer(ChatMsg.serializer())

    private fun file(uid: String, friend: String) = File(dir, "$uid-$friend.json")

    fun load(uid: String, friend: String): List<ChatMsg> =
        runCatching { JSON.decodeFromString(ser, file(uid, friend).readText()) }.getOrDefault(emptyList())

    fun save(uid: String, friend: String, msgs: List<ChatMsg>) {
        runCatching { file(uid, friend).writeText(JSON.encodeToString(ser, msgs.takeLast(300))) }
    }

    private fun key(uid: String, friend: String) = "u_${uid}_$friend"

    fun setUnread(uid: String, friend: String, n: Int) {
        val e = prefs.edit()
        if (n <= 0) e.remove(key(uid, friend)) else e.putInt(key(uid, friend), n)
        e.apply()
    }

    fun allUnread(uid: String): Map<String, Int> {
        val prefix = "u_${uid}_"
        val out = mutableMapOf<String, Int>()
        for ((k, v) in prefs.all) {
            if (k.startsWith(prefix) && v is Int) out[k.removePrefix(prefix)] = v
        }
        return out
    }
}
