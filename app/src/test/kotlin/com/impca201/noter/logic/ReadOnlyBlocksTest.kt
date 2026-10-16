package com.impca201.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadOnlyBlocksTest {
    @Test
    fun `names a file after its link`() {
        assertEquals("My report.pdf", ReadOnlyBlocks.fileName("https://files.example/abc/My%20report.pdf?sig=1"))
        assertEquals("example.com", ReadOnlyBlocks.host("https://www.example.com/a"))
    }

    @Test
    fun `links a page to Notion`() {
        assertEquals("https://www.notion.so/12ab34cd", ReadOnlyBlocks.notionUrl("12ab-34cd"))
    }
}
