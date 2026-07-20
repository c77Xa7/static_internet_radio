package com.staticradio.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Country code entry restricted to ISO 3166-1 alpha-2 — type to filter by
 * country name, select to commit. Stores the code (matches Radio Browser's
 * `countrycode` convention and the existing field), displays the name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value) { mutableStateOf(KnownCountries.nameForCode(value) ?: value) }
    val filtered = remember(query) {
        if (query.isBlank()) KnownCountries.all
        else KnownCountries.all.filter { it.second.contains(query, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.take(50).forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        query = name
                        onValueChange(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Language entry restricted to a known ISO 639-1 language name list — type
 * to filter, select to commit. Stores the display name directly, matching
 * how the language field is already populated from Radio Browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(value) { mutableStateOf(value) }
    val filtered = remember(query) {
        if (query.isBlank()) KnownLanguages.all
        else KnownLanguages.all.filter { it.contains(query, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.take(50).forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        query = name
                        onValueChange(name)
                        expanded = false
                    }
                )
            }
        }
    }
}
