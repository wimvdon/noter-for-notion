package be.wimvandonck.noter.data.sync

import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.NoteSummary
import be.wimvandonck.noter.data.model.PageIcon
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A change made on this phone that Notion doesn't have yet. Kept until it is sent. */
@Serializable
sealed interface PendingOp {
    val pageId: String

    @Serializable
    @SerialName("create")
    /**
     * A new note in [databaseId], or a page on its own (databaseId [be.wimvandonck.noter.data.NotesRepository.PAGES])
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

    /** The page's icon becomes [emoji], or is removed when it is null. */
    @Serializable
    @SerialName("icon")
    data class SetIcon(override val pageId: String, val emoji: String?) : PendingOp
}

/**
 * The queue of [PendingOp]s, sent in order. Adding keeps it short: a newer value of a property or
 * icon replaces the older one, and a note created on this phone sends its properties, icon and
 * content together with the create, so those edits need no op of their own.
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
            is PendingOp.SetIcon ->
                if (local) {
                    queue
                } else {
                    val existing = queue.indexOfFirst { it is PendingOp.SetIcon && it.pageId == op.pageId }
                    if (existing >= 0) queue.toMutableList().apply { set(existing, op) } else queue + op
                }
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
                    is PendingOp.SetIcon -> op.copy(pageId = newId)
                }
            }
        }

    /** [note] with the property values and icon still waiting to be sent, so a refresh doesn't undo them. */
    fun applyPending(note: NoteSummary, queue: List<PendingOp>): NoteSummary {
        val pending = queue.filter { it.pageId == note.id && (it is PendingOp.SetProperty || it is PendingOp.SetIcon) }
        if (pending.isEmpty()) return note
        return pending.fold(note) { acc, op ->
            when (op) {
                is PendingOp.SetProperty -> acc.withProperty(op.property)
                is PendingOp.SetIcon -> acc.copy(icon = op.emoji?.let { PageIcon(emoji = it) })
                else -> acc
            }
        }
    }
}

/** [this] with [property] set, also updating the title when it is the title property. */
fun NoteSummary.withProperty(property: NoteProperty): NoteSummary {
    val exists = properties.any { it.name == property.name }
    val updated = if (exists) properties.map { if (it.name == property.name) property else it } else properties + property
    val title = if (property.type == "title") {
        (property.value as? be.wimvandonck.noter.data.model.PropertyValue.Text)?.markdown.orEmpty()
    } else {
        this.title
    }
    return copy(properties = updated, title = title)
}
