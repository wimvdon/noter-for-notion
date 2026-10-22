package be.wimvandonck.noter.data.notion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleDirectoryTest {
    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

    private val directory = obj(
        """{"object":"data_source","id":"ds1","title":[{"plain_text":"People"}],"parent":{"type":"database_id","database_id":"db1"},
           "properties":{"Name":{"type":"title","title":{}},"Email":{"type":"email","email":{}},
             "Membership Type":{"type":"select","select":{"options":[{"name":"Workspace owner"},{"name":"Guest"},{"name":"Deactivated"}]}}}}""",
    )
    private val profile = obj(
        """{"object":"page","id":"p1","parent":{"type":"data_source_id","data_source_id":"ds1"},
           "properties":{"Name":{"type":"title","title":[{"plain_text":"Wim","text":{"content":"Wim"},"annotations":{}}]},
             "Email":{"type":"email","email":"wim@example.com"},
             "Membership Type":{"type":"select","select":{"name":"Workspace owner"}}}}""",
    )

    @Test
    fun `the People directory and its profiles are recognised`() {
        assertTrue(PeopleDirectory.isDirectory(directory))
        assertTrue(PeopleDirectory.isProfile(profile))
    }

    @Test
    fun `and left out of Home, but Search finds them`() {
        assertNull(WorkspaceCodec.parse(directory, includeNotes = false, pagesKey = "pages"))
        assertNull(WorkspaceCodec.parse(profile, includeNotes = true, pagesKey = "pages"))
        assertNotNull(WorkspaceCodec.parse(directory, includeNotes = true, pagesKey = "pages", includePeople = true))
        assertNotNull(WorkspaceCodec.parse(profile, includeNotes = true, pagesKey = "pages", includePeople = true))
    }

    @Test
    fun `a database of one's own about members is not the directory`() {
        // Its own membership types, or no email property: an ordinary database.
        val crm = obj(
            """{"object":"data_source","id":"ds2","title":[{"plain_text":"Members"}],
               "properties":{"Name":{"type":"title","title":{}},"Email":{"type":"email","email":{}},
                 "Membership Type":{"type":"select","select":{"options":[{"name":"Gold"},{"name":"Silver"}]}}}}""",
        )
        assertFalse(PeopleDirectory.isDirectory(crm))
        assertNotNull(WorkspaceCodec.parse(crm, includeNotes = false, pagesKey = "pages"))
        val noEmail = obj(
            """{"object":"page","id":"p2","properties":{"Name":{"type":"title","title":[]},
               "Membership Type":{"type":"select","select":{"name":"Member"}}}}""",
        )
        assertFalse(PeopleDirectory.isProfile(noEmail))
    }
}
