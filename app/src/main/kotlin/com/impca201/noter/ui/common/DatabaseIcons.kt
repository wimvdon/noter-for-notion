package com.impca201.noter.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** The icons a database tab can have, by the key stored in the settings. */
object DatabaseIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "note" to Icons.AutoMirrored.Rounded.StickyNote2,
        "checklist" to Icons.Rounded.Checklist,
        "list" to Icons.AutoMirrored.Rounded.ListAlt,
        "article" to Icons.AutoMirrored.Rounded.Article,
        "book" to Icons.AutoMirrored.Rounded.MenuBook,
        "idea" to Icons.Rounded.Lightbulb,
        "work" to Icons.Rounded.Work,
        "home" to Icons.Rounded.Home,
        "groceries" to Icons.Rounded.LocalGroceryStore,
        "food" to Icons.Rounded.Restaurant,
        "calendar" to Icons.Rounded.CalendarMonth,
        "people" to Icons.Rounded.People,
        "school" to Icons.Rounded.School,
        "code" to Icons.Rounded.Code,
        "build" to Icons.Rounded.Build,
        "money" to Icons.Rounded.AttachMoney,
        "travel" to Icons.Rounded.Flight,
        "health" to Icons.Rounded.FitnessCenter,
        "heart" to Icons.Rounded.Favorite,
        "star" to Icons.Rounded.Star,
        "flag" to Icons.Rounded.Flag,
        "bookmark" to Icons.Rounded.Bookmark,
        "folder" to Icons.Rounded.Folder,
        "box" to Icons.Rounded.Inventory2,
        "music" to Icons.Rounded.MusicNote,
        "movie" to Icons.Rounded.Movie,
        "art" to Icons.Rounded.Palette,
        "pets" to Icons.Rounded.Pets,
        "sparkle" to Icons.Rounded.AutoAwesome,
    )

    private val byKey = all.toMap()

    fun icon(key: String?): ImageVector = byKey[key] ?: Icons.AutoMirrored.Rounded.StickyNote2
}
