package com.sara.voicelock

enum class Command { LOCK, UNLOCK }

/**
 * Turns recognised speech into a command.
 * Works for: "Hey Sara, lock my phone" / "Hey Sara, unlock my phone".
 * Speech engines often hear the name differently, so several spellings are accepted.
 */
object CommandParser {

    private val wakeWords = listOf("sara", "sarah", "sera", "saara", "sarra", "सारा", "सरा")

    fun parse(raw: String): Command? {
        val t = raw.lowercase().trim()
        if (wakeWords.none { t.contains(it) }) return null

        // Check "unlock" first because the word "unlock" contains "lock".
        return when {
            t.contains("unlock") || t.contains("un lock") || t.contains("अनलॉक") -> Command.UNLOCK
            t.contains("lock") || t.contains("लॉक") -> Command.LOCK
            else -> null
        }
    }
}
