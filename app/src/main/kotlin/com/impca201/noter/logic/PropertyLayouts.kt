package com.impca201.noter.logic

import com.impca201.noter.data.model.NoteProperty
import com.impca201.noter.data.model.PropertyLayout

/**
 * Which of a note's properties are shown, in which order, and which appear on its card
 * ([PropertyLayout], one per database). By default the first [DEFAULT_SHOWN] are shown and the
 * rest are collapsed. The title is never part of it (it has its own field).
 */
object PropertyLayouts {
    const val DEFAULT_SHOWN = 3
    const val MAX_ON_CARD = 3

    /** A note's properties, split into the shown ones and the collapsed ones, in the user's order. */
    data class Arranged(val shown: List<NoteProperty>, val hidden: List<NoteProperty>)

    /** [names] in the user's order: the ordered ones first, then the rest as they come. */
    fun ordered(names: List<String>, layout: PropertyLayout?): List<String> {
        val order = layout?.order.orEmpty()
        return order.filter { it in names } + names.filterNot { it in order }
    }

    fun shownNames(names: List<String>, layout: PropertyLayout?): Set<String> =
        layout?.shown?.toSet() ?: ordered(names, layout).take(DEFAULT_SHOWN).toSet()

    fun arrange(properties: List<NoteProperty>, layout: PropertyLayout?): Arranged {
        val byName = properties.filter { it.type != "title" }.associateBy { it.name }
        val names = ordered(byName.keys.toList(), layout)
        val shown = shownNames(names, layout)
        val (visible, hidden) = names.mapNotNull { byName[it] }.partition { it.name in shown }
        return Arranged(visible, hidden)
    }

    /** The layout after the user put the properties in the order [names]. */
    fun reorder(layout: PropertyLayout?, names: List<String>): PropertyLayout =
        (layout ?: PropertyLayout()).copy(order = names)

    /** Shows or collapses [name]; the default (first three) becomes an explicit choice. */
    fun toggleShown(layout: PropertyLayout?, names: List<String>, name: String): PropertyLayout {
        val current = shownNames(names, layout)
        val shown = if (name in current) current - name else current + name
        return (layout ?: PropertyLayout()).copy(shown = ordered(names, layout).filter { it in shown })
    }

    /** Puts [name] on the card or takes it off; nothing changes when the card already has [MAX_ON_CARD]. */
    fun toggleOnCard(layout: PropertyLayout?, name: String): PropertyLayout {
        val base = layout ?: PropertyLayout()
        return when {
            name in base.onCard -> base.copy(onCard = base.onCard - name)
            base.onCard.size >= MAX_ON_CARD -> base
            else -> base.copy(onCard = base.onCard + name)
        }
    }

    /** Shows the header image on the cards, or not. */
    fun setCoverOnCard(layout: PropertyLayout?, shown: Boolean): PropertyLayout = (layout ?: PropertyLayout()).copy(coverOnCard = shown)

    /** Shows the start of the content on the cards, or not. */
    fun setContentOnCard(layout: PropertyLayout?, shown: Boolean): PropertyLayout = (layout ?: PropertyLayout()).copy(contentOnCard = shown)

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
