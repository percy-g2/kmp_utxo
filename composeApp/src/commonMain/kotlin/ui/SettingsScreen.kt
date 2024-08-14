package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import getCacheDirectoryPath
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath
import theme.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPress: () -> Unit
) {
    val store: KStore<Settings> = storeOf(file = "${getCacheDirectoryPath()}/settings.json".toPath())
    val coroutineScope = rememberCoroutineScope()
    val settings: Flow<Settings?> = store.updates
    val selectedTheme = settings.collectAsState(initial = Settings(selectedTheme = 0))
    var selectedOption by remember { mutableIntStateOf(0) }

    when (selectedTheme.value?.selectedTheme) {
        0 -> {
            selectedOption = 0
        }
        1 -> {
            selectedOption = 1
        }
        2 -> {
            selectedOption = 2
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Theme", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            LazyColumn {
                item {
                    ThemeOption(
                        title = "Use Device Settings",
                        description = "Upon activation, Day or Night mode will be followed by device settings.",
                        index = 0,
                        isSelected = selectedOption == 0,
                        onOptionSelected = {
                            coroutineScope.launch {
                                ThemeManager.updateTheme(it)
                            }
                        }
                    )
                }
                item {
                    ThemeOption(
                        title = "Light",
                        index = 1,
                        isSelected = selectedOption == 1,
                        onOptionSelected = {
                            coroutineScope.launch {
                                ThemeManager.updateTheme(it)
                            }
                        }
                    )
                }
                item {
                    ThemeOption(
                        title = "Dark",
                        index = 2,
                        isSelected = selectedOption == 2,
                        onOptionSelected = {
                            coroutineScope.launch {
                                ThemeManager.updateTheme(it)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    index: Int,
    description: String? = null,
    isSelected: Boolean,
    onOptionSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOptionSelected(index) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isSelected,
                onClick = { onOptionSelected(index) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (description != null) {
                    Text(
                        text = description,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Serializable
data class Settings(val selectedTheme: Int = 0)