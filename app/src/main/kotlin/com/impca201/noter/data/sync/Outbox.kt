package com.impca201.noter.data.sync

import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.NoteSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A change made on this phone that Notion doesn't have yet. Kept until it is sent. */
@Serializable
sealed interface PendingOp {
    val pageId: String

    @Serializable
    @SerialName("create")
    /**
     * A new note in [databaseId], or a page on its own (databaseId [com.impca201.noter.data.NotesRepository.PAGES])
     * inside page [parentPageId], or at the top of the workspace when that is null.
     */
    data class CreatePage(override val pageId: String, val databaseId: String, val parentPageId: String? = null) : PendingOp

    @Serializable
    @SerialName("property")
    data class SetProperty(override val pageId: String, val property: NoteProperty) : PendingOp

    /** The page's content in the cache has edits to send (see ContentDiff). */
    @Serializable
    @SerialName("content")
    data class SaveContent(override val pageId: String) : PendingOp

    @Serializable
    @SerialName("trash")
    data class TrashPage(override val pageId: String) : PendingOp
}

/**
 * The queue of [PendingOp]s, sent in order. Adding keeps it short: a newer value of a property
 * replaces the older one, and a note created on this phone sends its properties and content
 * together with the create, so those edits need no op of their own.
 */
object Outbox {
    fun add(queue: List<PendingOp>, op: PendingOp): List<PendingOp> {
        val local = op.pageId.startsWith(NoteSummary.LOCAL_PREFIX)
        return when (op) {
            is PendingOp.CreatePage -> queue + op
            is PendingOp.SetProperty ->
                if (local) {
                    queue
                } else {
                    val existing = queue.indexOfFirst {
                        it is PendingOp.SetProperty && it.pageId == op.pageId && it.property.name == op.property.name
                    }
                    if (existing >= 0) queue.toMutableList().apply { set(existing, op) } else queue + op
                }
            is PendingOp.SaveContent ->
                if (local || queue.any { it is PendingOp.SaveContent && it.pageId == op.pageId }) queue else queue + op
            is PendingOp.TrashPage ->
                if (local) {
                    queue.filterNot { it.pageId == op.pageId }
                } else {
                    queue.filterNot { it.pageId == op.pageId } + op
                }
        }
    }

    /** The queue after a note created on this phone got its Notion ID. */
    fun rename(queue: List<PendingOp>, oldId: String, newId: String): List<PendingOp> =
        queue.map { op ->
            if (op.pageId != oldId) {
                op
            } else {
                when (op) {
                    is PendingOp.CreatePage -> op.copy(pageId = newId)
                    is PendingOp.SetProperty -> op.copy(pageId = newId)
                    is PendingOp.SaveContent -> op.copy(pageId = newId)
                    is PendingOp.TrashPage -> op.copy(pageId = newId)
                }
            }
        }

    /** [note] with the property values still waiting to be sent, so a refresh doesn't undo them. */
    fun applyPending(note: NoteSummary, queue: List<PendingOp>): NoteSummary {
        val pending = queue.filterIsInstance<PendingOp.SetProperty>().filter { it.pageId == note.id }
        if (pending.isEmpty()) return note
        return pending.fold(note) { acc, op -> acc.withProperty(op.property) }
    }
}

/** [this] with [property] set, also updating the title when it is the title property. */
fun NoteSummary.withProperty(property: NoteProperty): NoteSummary {
    val exists = properties.any { it.name == property.name }
    val updated = if (exists) properties.map { if (it.name == property.name) property else it } else properties + property
    val title = if (property.type == "title") {
        (property.value as? com.impca201.noter.data.model.PropertyValue.Text)?.markdown.orEmpty()
    } else {
        this.title
    }
    return copy(properties = updated, title = title)
}
