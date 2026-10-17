package be.wimvandonck.noter.data.notion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewCodecTest {
    @Test
    fun `reads what a notebook starts from`() {
        val view = ViewCodec.parse(
            Json.parseToJsonElement(
                """{"object":"view","id":"v1","name":"Board","type":"board",
                   "quick_filters":{"Status":{"status":{"equals":"In progress"}},"Owner":{}},
                   "configuration":{"type":"board","group_by":{"type":"status","property_id":"st"},
                     "properties":[{"property_id":"title","visible":true},{"property_id":"tg","visible":false}],
                     "cover":{"type":"page_cover"}}}""",
            ).jsonObject,
        )!!
        assertEquals("Board", view.name)
        assertEquals(listOf("Status", "Owner"), view.quickFilters)
        assertEquals("st", view.groupBy)
        assertEquals(listOf("title" to true, "tg" to false), view.properties)
        assertTrue(view.showsCover)
        assertTrue(ViewCodec.isNotebookKind(view))
        assertFalse(ViewCodec.isNotebookKind(view.copy(type = "form")))
    }
}
