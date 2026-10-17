package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertyValue
import org.junit.Assert.assertEquals
import org.junit.Test

class KnownUsersTest {
    private fun note(vararg users: NotionUser) =
        NoteSummary("p", "db", "", properties = listOf(NoteProperty("Owner", "people", PropertyValue.People(users.toList()))))

    @Test
    fun `the owner and the people in the notes, once each, by name`() {
        val wim = NotionUser("u-1", "Wim")
        val an = NotionUser("u2", "An")
        val users = KnownUsers.of(listOf(note(an, NotionUser("u1", "Wim")), note(an)), owner = wim)
        assertEquals(listOf(an, wim), users)
    }

    @Test
    fun `people without a name are left out`() {
        assertEquals(emptyList<NotionUser>(), KnownUsers.of(listOf(note(NotionUser("x", ""))), owner = null))
    }
}
