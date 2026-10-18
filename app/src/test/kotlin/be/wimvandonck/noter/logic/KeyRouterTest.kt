package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyRouterTest {
    @Test
    fun `an unknown id tries every token in order`() {
        assertEquals(listOf("a", "b"), KeyRouter().candidates("page", listOf("a", "b")))
        assertEquals(listOf("a", "b"), KeyRouter().candidates(null, listOf("a", "b")))
    }

    @Test
    fun `the token that worked comes first`() {
        val router = KeyRouter()
        router.remember("page", "b")
        assertEquals(listOf("b", "a", "c"), router.candidates("page", listOf("a", "b", "c")))
    }

    @Test
    fun `ids match with or without dashes and in any case`() {
        val router = KeyRouter()
        router.remember("1234ABCD-5678", "b")
        assertEquals("b", router.keyOf("1234abcd5678"))
        assertEquals(listOf("b", "a"), router.candidates("1234abcd-5678", listOf("a", "b")))
    }

    @Test
    fun `a removed token is not tried`() {
        val router = KeyRouter()
        router.remember("page", "b")
        assertEquals(listOf("a"), router.candidates("page", listOf("a")))
    }

    @Test
    fun `blank ids and tokens are not remembered`() {
        val router = KeyRouter()
        router.remember("", "b")
        router.remember("page", "")
        assertNull(router.keyOf(""))
        assertNull(router.keyOf("page"))
    }
}
