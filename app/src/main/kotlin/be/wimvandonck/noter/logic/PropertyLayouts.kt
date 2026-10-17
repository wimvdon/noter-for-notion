package be.wimvandonck.noter.logic

import be.wimvandonck.noter.data.model.CardLayout
import be.wimvandonck.noter.data.model.DatabaseConfig
import be.wimvandonck.noter.data.model.NoteProperty
import be.wimvandonck.noter.data.model.PropertyLayout

/**
 * Which of a note's properties are shown, in which order, and which appear on its card
 * ([PropertyLayout], one per database). As in Notion, all of them are shown until the user hides
 * some; hidden ones are folded away. The title is never part of it (it has its own field).
 */
object PropertyLayouts {
    const val MAX_ON_CARD = 3

    /** A note's properties, split into the shown ones and the hidden ones, in the user's order. */
    data class Arranged(val shown: List<NoteProperty>, val hidden: List<NoteProperty>)

    /** [names] in the user's order: the ordered ones first, then the rest as they come. */
    fun ordered(names: List<String>, layout: PropertyLayout?): List<String> {
        val order = layout?.order.orEmpty()
        return order.filter { it in names } + names.filterNot { it in order }
    }

    /** [names] in the user's order, split into the shown ones and the hidden ones. */
    fun sections(names: List<String>, layout: PropertyLayout?): Pair<List<String>, List<String>> {
        val hidden = layout?.hidden.orEmpty().toSet()
        return ordered(names, layout).partition { it !in hidden }
    }

    fun arrange(properties: List<NoteProperty>, layout: PropertyLayout?): Arranged {
        val byName = properties.filter { it.type != "title" }.associateBy { it.name }
        val (shown, hidden) = sections(byName.keys.toList(), layout)
        return Arranged(shown.mapNotNull { byName[it] }, hidden.mapNotNull { byName[it] })
    }

    /** The layout after the user arranged the properties: [shown] in this order, then [hidden]. */
    fun arranged(layout: PropertyLayout?, shown: List<String>, hidden: List<String>): PropertyLayout =
        (layout ?: PropertyLayout()).copy(order = shown + hidden, hidden = hidden)

    /** Shows or hides [name]: it moves to the end of the shown ones, or the top of the hidden ones. */
    fun toggleShown(layout: PropertyLayout?, names: List<String>, name: String): PropertyLayout {
        val (shown, hidden) = sections(names, layout)
        return if (name in shown) arranged(layout, shown - name, listOf(name) + hidden) else arranged(layout, shown + name, hidden - name)
    }

    /**
     * What [notebook]'s cards show: its own choice, else what its database's cards showed before
     * cards were set per notebook ([layout]).
     */
    fun cards(notebook: DatabaseConfig?, layout: PropertyLayout?): CardLayout =
        notebook?.cards ?: CardLayout(layout?.coverOnCard == true, layout?.contentOnCard != false, layout?.onCard.orEmpty())

    /** Puts [name] on the card or takes it off; nothing changes when the card already has [MAX_ON_CARD]. */
    fun toggleOnCard(cards: CardLayout, name: String): CardLayout = when {
        name in cards.properties -> cards.copy(properties = cards.properties - name)
        cards.properties.size >= MAX_ON_CARD -> cards
        else -> cards.copy(properties = cards.properties + name)
    }

    /**
     * Each database's layout with what its cards show filled in, for the overview: from [current]
     * (the notebook shown) for its database, else from the first notebook of the database.
     */
    fun cardLayouts(layouts: Map<String, PropertyLayout>, notebooks: List<DatabaseConfig>, current: DatabaseConfig? = null): Map<String, PropertyLayout> {
        val sources = layouts.keys + notebooks.map { it.sourceId }
        return sources.associateWith { source ->
            val notebook = current?.takeIf { it.sourceId == source } ?: notebooks.firstOrNull { it.sourceId == source }
            val cards = cards(notebook, layouts[source])
            (layouts[source] ?: PropertyLayout()).copy(coverOnCard = cards.cover, contentOnCard = cards.content, onCard = cards.properties)
        }
    }

    /** True when a card shows its note's header image (only when the note has one). */
    fun showsCover(layout: PropertyLayout?, cover: String?): Boolean = cover != null && layout?.coverOnCard == true

    /** True when a card shows the start of its note's content (so the preview is needed). */
    fun showsContent(layout: PropertyLayout?): Boolean = layout?.contentOnCard != false

    /** The properties shown on a card, in the user's order. */
    fun cardProperties(properties: List<NoteProperty>, layout: PropertyLayout?): List<NoteProperty> {
        val onCard = layout?.onCard.orEmpty()
        if (onCard.isEmpty()) return emptyList()
        val byName = properties.associateBy { it.name }
        return ordered(properties.map { it.name }, layout).filter { it in onCard }.mapNotNull { byName[it] }
    }
}
