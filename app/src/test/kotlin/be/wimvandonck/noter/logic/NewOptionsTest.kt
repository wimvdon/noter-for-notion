package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewOptionsTest {
    @Test
    fun `a new name is offered, tidied up`() {
        assertEquals("Next week", NewOptions.offer("  Next   week ", listOf("Today")))
    }

    @Test
    fun `nothing is offered for an empty query, a comma or a name that exists in any case`() {
        assertNull(NewOptions.offer("   ", listOf("Today")))
        assertNull(NewOptions.offer("a, b", emptyList()))
        assertNull(NewOptions.offer("today", listOf("Today")))
    }
}
