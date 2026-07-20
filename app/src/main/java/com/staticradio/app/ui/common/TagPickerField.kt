package com.staticradio.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
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
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.local.TagType

/**
 * Single-value selection from a Settings-managed vocabulary (Mood/Style) —
 * always user-defined, no free text, same "pick from a known list" model as
 * GenreVocabularyField but single-select instead of multi-tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerField(
    stationDao: StationDao,
    tagType: TagType,
    value: String?,
    onValueChange: (String?) -> Unit,
    label: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    val vocabulary by stationDao.observeTagsByType(tagType).collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = {
                Text(if (vocabulary.isEmpty()) "No $label options defined — add some in Settings" else "Tap to select")
            },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && vocabulary.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            if (value != null) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = { onValueChange(null); expanded = false }
                )
            }
            vocabulary.forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag.name) },
                    onClick = { onValueChange(tag.name); expanded = false }
                )
            }
        }
    }
}
