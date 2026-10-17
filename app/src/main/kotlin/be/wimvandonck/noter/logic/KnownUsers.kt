package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.NotionUser
import be.wimvandonck.noter.data.model.PropertyValue

/**
 * The people to choose from for a person property when Notion won't list the workspace's members
 * (a personal access token can't): the people already in the notes on this phone, and the
 * token's owner, by name.
 */
object KnownUsers {
    fun of(notes: Collection<NoteSummary>, owner: NotionUser?, others: List<NotionUser> = emptyList()): List<NotionUser> {
        val people = notes.flatMap { note -> note.properties.mapNotNull { it.value as? PropertyValue.People }.flatMap { it.users } }
        return (listOfNotNull(owner) + others + people)
            .filter { it.name.isNotBlank() }
            .distinctBy { it.id.replace("-", "") }
            .sortedBy { it.name.lowercase() }
    }
}
