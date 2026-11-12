package be.wimvandonck.noter.data.notion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewCodecTest {
    private fun parse(text: String) = ViewCodec.parse(Json.parseToJsonElement(text).jsonObject)

    @Test
    fun `a view is read with its filter, sorts and quick filters`() {
        val view = parse(
            """{"object":"view","id":"v1","name":"Open","type":"board","data_source_id":"ds1",
               "parent":{"type":"database_id","database_id":"db1"},
               "filter":{"property":"Done","checkbox":{"equals":false}},
               "sorts":[{"timestamp":"created_time","direction":"descending"}],
               "quick_filters":{"Status":{}},"configuration":{"type":"board"}}""",
        )!!
        assertEquals("v1", view.id)
        assertEquals("Open", view.name)
        assertEquals("board", view.type)
        assertEquals("ds1", view.dataSourceId)
        assertEquals(Json.parseToJsonElement("""{"property":"Done","checkbox":{"equals":false}}"""), view.filter)
        assertEquals(Json.parseToJsonElement("""[{"timestamp":"created_time","direction":"descending"}]"""), view.sorts)
        assertEquals(Json.parseToJsonElement("""{"Status":{}}"""), view.quickFilters)
    }

    @Test
    fun `what isn't set is null, and what isn't a view is nothing`() {
        val view = parse("""{"object":"view","id":"v1","type":"dashboard","data_source_id":null,"filter":null}""")!!
        assertNull(view.dataSourceId)
        assertNull(view.filter)
        assertNull(view.sorts)
        assertEquals("", view.name)
        assertNull(parse("""{"object":"page","id":"p1"}"""))
    }
}
