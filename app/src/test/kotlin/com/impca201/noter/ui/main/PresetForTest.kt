package com.impca201.noter.ui.main

import com.impca201.noter.data.model.PropertySchema
import com.impca201.noter.data.model.PropertyValue
import com.impca201.noter.logic.ChipFilter
import com.impca201.noter.logic.FilterChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PresetForTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun `a new note gets the selected filter's value where it has one`() {
        val due = PropertySchema(name = "Due", type = "date")
        assertEquals(PropertyValue.Date("2026-10-17"), presetFor(due, FilterChip(ChipFilter.TODAY, ""), today)?.value)
        assertNull(presetFor(due, FilterChip(ChipFilter.OVERDUE, ""), today))

        val project = PropertySchema(name = "Project", type = "relation")
        assertEquals(PropertyValue.Relation(listOf("p1")), presetFor(project, FilterChip("p1", ""), today)?.value)

        // Notion computes these itself.
        assertNull(presetFor(PropertySchema(name = "By", type = "created_by"), FilterChip("Wim", "Wim"), today))
        assertNull(presetFor(PropertySchema(name = "F", type = "formula"), FilterChip(ChipFilter.CHECKED, ""), today))
    }
}
