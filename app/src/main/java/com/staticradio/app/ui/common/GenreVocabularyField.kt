package com.staticradio.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.staticradio.app.data.GenreTags
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.TagType

/**
 * Genre entry restricted to the managed vocabulary (Settings → Genre
 * Vocabulary) — no free text. Selecting/deselecting a tag rebuilds the same
 * comma-joined string the rest of the genre pipeline (GenreTags.parse,
 * *Source / *Override) already expects, so nothing downstream changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreVocabularyField(
    stationDao: StationDao,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    val vocabulary by stationDao.observeTagsByType(TagType.GENRE).collectAsState(initial = emptyList())
    val selected = remember(value) { GenreTags.parse(value).toSet() }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (vocabulary.isNotEmpty()) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.joinToString(", "),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = {
                Text(if (vocabulary.isEmpty()) "No genres defined — add some in Settings → Genre Vocabulary" else "Tap to select")
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && vocabulary.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            vocabulary.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag.name) },
                    leadingIcon = {
                        Checkbox(checked = tag.name in selected, onCheckedChange = null)
                    },
                    onClick = {
                        val next = if (tag.name in selected) selected - tag.name else selected + tag.name
                        onValueChange(next.joinToString(", "))
                    }
                )
            }
        }
    }
}
