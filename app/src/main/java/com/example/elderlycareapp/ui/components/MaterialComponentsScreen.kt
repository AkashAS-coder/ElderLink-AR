package com.example.elderlycareapp.ui.components
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialComponentsScreen() {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var switchChecked by remember { mutableStateOf(false) }
    var radioOption by remember { mutableStateOf("Option 1") }
    var checkboxState by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // App Bar
        TopAppBar(
            title = { Text("Material 3 Components") },
            navigationIcon = {
                IconButton(onClick = { /* Handle back */ }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                IconButton(onClick = { /* Handle search */ }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
                IconButton(onClick = { /* Handle more */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = {}) {
                Text("Filled Tonal")
            }
            OutlinedButton(onClick = {}) {
                Text("Outlined")
            }
            TextButton(onClick = {}) {
                Text("Text")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cards
        Card(
            onClick = { /* Handle click */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Card Title", style = MaterialTheme.typography.titleLarge)
                Text("This is a card with some content inside it.")
                TextButton(onClick = {}) {
                    Text("Action")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Simple chips implemented with stable Buttons (avoids experimental FlowRow/SuggestionChip API differences)
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedButton(onClick = {}) {
                Text("Chip")
            }

            OutlinedButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("With Icon")
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "Selected",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text Field
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { textFieldValue = it },
            label = { Text("Text Field") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            trailingIcon = { 
                if (textFieldValue.isNotEmpty()) {
                    IconButton(onClick = { textFieldValue = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Slider
        Text("Slider: ${sliderPosition.toInt()}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            valueRange = 0f..100f,
            steps = 10,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Switch and Checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Switch(
                checked = switchChecked,
                onCheckedChange = { switchChecked = it }
            )
            Text("Toggle Switch")
            
            Checkbox(
                checked = checkboxState,
                onCheckedChange = { checkboxState = it }
            )
            Text("Checkbox")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Radio Buttons
        Text("Radio Group:")
        Column {
            listOf("Option 1", "Option 2", "Option 3").forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (option == radioOption),
                            onClick = { radioOption = option }
                        )
                        .padding(8.dp)
                ) {
                    RadioButton(
                        selected = (option == radioOption),
                        onClick = { radioOption = option }
                    )
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Floating Action Button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            FloatingActionButton(
                onClick = { /* Handle FAB click */ },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    }
}
