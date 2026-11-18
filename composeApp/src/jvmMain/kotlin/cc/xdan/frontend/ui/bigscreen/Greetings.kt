package cc.xdan.frontend.ui.bigscreen

import kotlin.random.Random

/** Home greeting: time-of-day lines, with sillier ones in the lazy middle of the afternoon. */
object Greetings {
    private val morning = listOf("Good morning", "Morning", "Rise and shine", "Fresh start")
    private val earlyAfternoon = listOf("Good afternoon", "Afternoon", "Hope lunch was good")
    private val lateAfternoon = listOf(
        "Good afternoon", "Ready player one", "Nice to see you", "Press start", "Back for more",
        "A fine time for a quick round", "Just one more level", "Welcome back", "Loading fun",
    )
    private val evening = listOf("Good evening", "Evening", "Time to unwind", "Settle in")
    private val night = listOf("Burning the midnight oil", "Still up", "Night owl mode", "One more game")

    fun forHour(hour: Int, seed: Long): String {
        val list = when (hour) {
            in 5..11 -> morning
            in 12..13 -> earlyAfternoon
            in 14..17 -> lateAfternoon
            in 18..21 -> evening
            else -> night
        }
        return list[Random(seed).nextInt(list.size)]
    }

    /** "[Greeting], [name]." or "[Greeting]." */
    fun line(greeting: String, name: String?): String = if (name.isNullOrBlank()) "$greeting." else "$greeting, ${name.trim()}."

    /** PersonaName of the most recent login in Steam's loginusers.vdf (or the only account). */
    fun steamPersona(vdf: String): String? {
        val blocks = Regex("\"\\d{17}\"\\s*\\{([^}]*)\\}").findAll(vdf).map { it.groupValues[1] }.toList()
        fun field(b: String, k: String) = Regex("\"$k\"\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(b)?.groupValues?.get(1)
        val pick = blocks.firstOrNull { field(it, "MostRecent") == "1" } ?: blocks.singleOrNull() ?: return null
        return field(pick, "PersonaName")?.takeIf { it.isNotBlank() }
    }
}
