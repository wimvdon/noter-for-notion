package com.impca201.noter.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.impca201.noter.data.model.SelectOption

/** A select, status or multi-select option in Notion's colors. */
@Composable
fun OptionChip(option: SelectOption, modifier: Modifier = Modifier, small: Boolean = false) {
    val colors = optionColors(option.color)
    Text(
        option.name,
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
        color = colors.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.container)
            .padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 2.dp else 4.dp),
    )
}
