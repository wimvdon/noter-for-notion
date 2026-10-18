package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebArticlesTest {
    private val page = """
        <html><head>
          <title>Ignored when there is og:title</title>
          <meta property="og:title" content="Soup &amp; bread">
          <meta property="og:image" content="https://cdn.example.com/lead.jpg">
          <script>var x = "<p>not this</p>";</script>
          <style>p { color: red }</style>
        </head><body>
          <nav><p>Menu</p></nav>
          <article>
            <h1>Soup &amp; bread</h1>
            <p>First   paragraph<br>with a break &#8211; and a <a href="/x">link</a>.</p>
            <h2>Steps</h2>
            <ul><li>Chop *onions*</li><li>Boil</li></ul>
            <figure><img src="/img/pot.png" alt="Pot"><figcaption>A pot</figcaption></figure>
            <img src="https://track.example.com/p.gif" width="1" height="1">
            <img src="data:image/png;base64,AAA" data-src="//cdn.example.com/lazy.jpg">
            <pre><code>a &lt; b
c</code></pre>
            <blockquote><p>Said someone</p></blockquote>
            <footer><p>Copyright</p></footer>
          </article>
        </body></html>
    """.trimIndent()

    @Test
    fun `reads the article's content in order, without scripts and menus`() {
        val article = WebArticles.parse(page, "https://example.com/recipes/soup")
        assertEquals("Soup & bread", article.title)
        val shown = article.blocks.map { it.type to (it.url ?: it.text) }
        assertEquals(
            listOf(
                BlockType.UNSUPPORTED to "https://cdn.example.com/lead.jpg",
                BlockType.PARAGRAPH to "First paragraph\nwith a break – and a link.",
                BlockType.HEADING_2 to "Steps",
                BlockType.BULLETED to "Chop \\*onions\\*",
                BlockType.BULLETED to "Boil",
                BlockType.UNSUPPORTED to "https://example.com/img/pot.png",
                BlockType.UNSUPPORTED to "https://cdn.example.com/lazy.jpg",
                BlockType.CODE to "a < b\nc",
                BlockType.QUOTE to "Said someone",
            ),
            shown,
        )
        assertTrue(article.blocks.filter { it.type == BlockType.UNSUPPORTED }.all { it.rawType == SharedNotes.IMAGE })
        assertEquals(article.blocks.size, article.blocks.map { it.key }.toSet().size)
    }

    @Test
    fun `a page without readable content gives its description`() {
        val article = WebArticles.parse(
            """<html><head><title>App</title><meta name="description" content="A web app"></head><body><div id="root"></div></body></html>""",
            "https://example.com",
        )
        assertEquals("App", article.title)
        assertEquals(listOf("A web app"), article.blocks.map { it.text })
    }

    @Test
    fun `without an article, the body is read`() {
        val article = WebArticles.parse("<body><p>Hello</p><p> </p></body>", "https://example.com")
        assertEquals("", article.title)
        assertEquals(listOf("Hello"), article.blocks.map { it.text })
    }
}
