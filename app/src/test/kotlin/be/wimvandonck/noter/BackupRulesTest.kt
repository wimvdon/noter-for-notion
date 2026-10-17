package be.wimvandonck.noter

import be.wimvandonck.noter.data.store.DataFiles
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards what Android's backup and phone-to-phone transfer may copy
 * (res/xml/data_extraction_rules.xml). See docs/architecture.md, "Backup".
 */
class BackupRulesTest {
    // Unit tests run in the app module's folder.
    private val rulesFile = File("src/main/res/xml/data_extraction_rules.xml")

    /** Only the settings DataStore; never the API key or the Notion cache. */
    private val onlySettings = listOf("include file datastore/${DataFiles.SETTINGS}.preferences_pb")

    private fun rules(section: String): List<String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(rulesFile).documentElement
        val children = (root.getElementsByTagName(section).item(0) as Element).childNodes
        return (0 until children.length)
            .map { children.item(it) }
            .filterIsInstance<Element>()
            .map { "${it.tagName} ${it.getAttribute("domain")} ${it.getAttribute("path")}" }
    }

    @Test
    fun `backup copies only the settings file`() {
        assertEquals(onlySettings, rules("cloud-backup"))
    }

    @Test
    fun `phone-to-phone transfer copies only the settings file`() {
        assertEquals(onlySettings, rules("device-transfer"))
    }
}
