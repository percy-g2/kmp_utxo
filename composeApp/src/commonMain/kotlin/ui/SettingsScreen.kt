package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import openLink
import org.jetbrains.compose.resources.painterResource
import theme.ThemeManager
import theme.ThemeManager.store
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.github_icon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPress: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val settings: Flow<Settings?> = store.updates
    val selectedTheme by settings.collectAsState(initial = Settings())

    val themesList = Theme.entries.map { theme ->
        ThemeData(theme.title, theme.description)
    }

    val isDarkTheme = (selectedTheme?.selectedTheme == Theme.DARK.id
        || (selectedTheme?.selectedTheme == Theme.SYSTEM.id && isSystemInDarkTheme()))

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.shadow(
                    elevation = 4.dp,
                    ambientColor = if (isDarkTheme) Color.White else Color.LightGray,
                    spotColor = if (isDarkTheme) Color.White else Color.LightGray
                ),
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(
                    top = 0.dp,
                    bottom = 0.dp
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            Row(
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Theme",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    itemsIndexed(themesList) { index, theme ->
                        ThemeOption(
                            title = theme.title,
                            description = theme.description,
                            index = index,
                            isSelected = (selectedTheme?.selectedTheme ?: Theme.SYSTEM.id) == index,
                            onOptionSelected = {
                                coroutineScope.launch {
                                    ThemeManager.updateTheme(index)
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.End),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        modifier = Modifier.size(24.dp),
                        onClick = { openLink("https://github.com/percy-g2/kmp_utxo") }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.github_icon),
                            contentDescription = "Source Code",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Version 0.0.6"
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
                description?.let {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Serializable
data class Settings(
    val selectedTheme: Int = Theme.SYSTEM.id,
    val favPairs: List<String> = listOf("BTCUSDT")
)

data class ThemeData(
    val title: String,
    val description: String? = null
)

enum class Theme(val id: Int, val title: String, val description: String? = null) {
    SYSTEM(
        id = 0,
        title = "Use Device Settings",
        description = "Upon activation, Day or Night mode will be followed by device settings."
    ),
    LIGHT(id = 1, title = "Light"),
    DARK(id = 2, title = "Dark")
}