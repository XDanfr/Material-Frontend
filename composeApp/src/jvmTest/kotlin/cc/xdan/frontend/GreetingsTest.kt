package cc.xdan.frontend

import cc.xdan.frontend.ui.bigscreen.Greetings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GreetingsTest {
    @Test fun lines() {
        assertEquals("Good morning, Sam.", Greetings.line("Good morning", "Sam "))
        assertEquals("Good morning.", Greetings.line("Good morning", ""))
        assertTrue(Greetings.forHour(8, 1).isNotBlank()); assertTrue(Greetings.forHour(3, 1).isNotBlank())
        assertTrue((0L until 50).map { Greetings.forHour(15, it) }.toSet().size > 3, "afternoon has variety")
    }

    @Test fun steamPersona() {
        val vdf = """
"users"
{
	"76561198000000001"
	{
		"AccountName"		"old"
		"PersonaName"		"Old Name"
		"MostRecent"		"0"
	}
	"76561198000000002"
	{
		"AccountName"		"acct"
		"PersonaName"		"Player Two"
		"MostRecent"		"1"
	}
}"""
        assertEquals("Player Two", Greetings.steamPersona(vdf))
        assertNull(Greetings.steamPersona("\"users\" { }"))
    }
}
