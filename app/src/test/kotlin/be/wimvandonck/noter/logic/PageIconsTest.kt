package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.icons.IconCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageIconsTest {
    private val catalog = IconCatalog(
        listOf(
            IconCatalog.Icon("local-pizza", listOf("food"), emptyList()),
            IconCatalog.Icon("book", listOf("reading"), emptyList()),
            IconCatalog.Icon("bookmark", listOf("save"), emptyList()),
        ),
    )

    @Test
    fun `the same name wins, else the best search match`() {
        assertEquals("book", PageIcons.closestSymbol("book", catalog)?.name)
        assertEquals("local-pizza", PageIcons.closestSymbol("pizza", catalog)?.name)
        assertEquals("book", PageIcons.closestSymbol("book-closed", catalog)?.name)
    }

    @Test
    fun `no match is no icon`() {
        assertNull(PageIcons.closestSymbol("zebra", catalog))
    }
}
