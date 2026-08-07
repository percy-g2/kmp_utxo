package ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import copyToClipboard
import ktx.buildStyledSymbol
import kotlinx.coroutines.delay
import model.ChatRole
import model.CoinChatMessage
import model.RssProvider
import model.Ticker24hr
import network.AiInsightService
import org.jetbrains.compose.resources.stringResource
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.back
import utxo.composeapp.generated.resources.chat_clear
import utxo.composeapp.generated.resources.chat_copied
import utxo.composeapp.generated.resources.chat_copy
import utxo.composeapp.generated.resources.chat_disclaimer
import utxo.composeapp.generated.resources.chat_error
import utxo.composeapp.generated.resources.chat_greeting
import utxo.composeapp.generated.resources.chat_input_hint
import utxo.composeapp.generated.resources.chat_rate_limited
import utxo.composeapp.generated.resources.chat_retry
import utxo.composeapp.generated.resources.chat_send
import utxo.composeapp.generated.resources.chat_sug_why_down
import utxo.composeapp.generated.resources.chat_sug_why_up
import utxo.composeapp.generated.resources.chat_suggestions_title
import utxo.composeapp.generated.resources.chat_thinking
import utxo.composeapp.generated.resources.chat_title
import utxo.composeapp.generated.resources.portfolio_open_settings
import kotlin.math.abs

/** Keeps a bubble to a readable line length on a desktop window as well as on a phone. */
private val MaxBubbleWidth = 340.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinChatScreen(
    symbol: String,
    displaySymbol: String,
    onBackClick: () -> Unit,
    /** Pre-fills and sends a question the user already chose by tapping a chip on the coin screen. */
    initialQuestion: String? = null,
    viewModel: CoinChatViewModel = viewModel { CoinChatViewModel() },
    /** Navigates to Settings so a rate-limited user can add an llm7.io token. Null hides the shortcut. */
    onOpenSettings: (() -> Unit)? = null
) {
    val settingsState by SettingsStore.settings.collectAsState()
    val state by viewModel.state.collectAsState()
    val baseAsset = remember(symbol) { AiInsightService.extractBaseAsset(symbol) }

    val enabledProviders = settingsState?.enabledRssProviders ?: RssProvider.DEFAULT_ENABLED_PROVIDERS
    val aiApiToken = settingsState?.aiApiToken ?: ""

    // null settings means "not read from disk yet" (see SettingsStore), NOT "defaults" — starting
    // on the placeholder would fetch news for the wrong provider set.
    val settingsLoaded = settingsState != null

    LaunchedEffect(settingsLoaded) {
        if (!settingsLoaded) return@LaunchedEffect
        viewModel.start(symbol, aiApiToken, enabledProviders.toSet(), initialQuestion)
    }

    // The token can change while this screen is alive — on the iOS 26 native tab bar nothing is
    // torn down while Settings is edited.
    LaunchedEffect(aiApiToken, settingsLoaded) {
        if (settingsLoaded) viewModel.updateApiToken(aiApiToken)
    }

    val listState = rememberLazyListState()

    // Follow the conversation as it grows, as the thinking bubble appears and is replaced, and as a
    // failure notice takes its place.
    LaunchedEffect(state.messages.size, state.isAwaitingReply, state.error, state.rateLimited) {
        // An empty thread is the browsable catalogue, which must stay at the top where the greeting
        // and the first category are — scrolling it to the end would bury both.
        if (state.messages.isEmpty()) return@LaunchedEffect
        // Wait out the frame this change composed in. `layoutInfo` reports the *last* measure pass,
        // so reading it here without waiting would scroll to where the list ended a bubble ago.
        withFrameNanos { }
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Column {
                        Text(displaySymbol.buildStyledSymbol())
                        Text(
                            text = stringResource(Res.string.chat_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearConversation() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(Res.string.chat_clear)
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(top = 0.dp, bottom = 0.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(top = paddingValues.calculateTopPadding()))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                // A conversation stacks up from the composer, as every chat does — top-aligning it
                // stranded a short exchange at the top with a void above the input. The catalogue
                // on an empty thread is a list to read from the beginning, so it stays top-aligned.
                verticalArrangement = if (state.messages.isEmpty()) {
                    Arrangement.spacedBy(12.dp)
                } else {
                    Arrangement.spacedBy(12.dp, Alignment.Bottom)
                }
            ) {
                if (state.messages.isEmpty()) {
                    item("greeting") { GreetingBubble(baseAsset) }

                    // The full catalogue lives here rather than above the input: there are a
                    // hundred questions and only room for a handful in the composer, and on an
                    // empty thread this area is blank anyway.
                    if (state.catalog.isNotEmpty()) {
                        item("suggestions-title") {
                            Text(
                                text = stringResource(Res.string.chat_suggestions_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        state.catalog.forEach { group ->
                            item(key = "cat-${group.category.name}") {
                                SuggestionGroupSection(
                                    group = group,
                                    baseAsset = baseAsset,
                                    ticker = state.ticker,
                                    onClick = viewModel::send
                                )
                            }
                        }
                    }
                }

                items(items = state.messages, key = { it.id }) { message ->
                    ChatBubble(message)
                }

                if (state.isAwaitingReply) {
                    item("thinking") { ThinkingBubble() }
                }

                if (state.error != null) {
                    item("error") {
                        ChatNotice(
                            text = stringResource(Res.string.chat_error),
                            isError = true,
                            onRetry = { viewModel.retryLast() }
                        )
                    }
                }

                if (state.rateLimited) {
                    item("rate-limited") {
                        ChatNotice(
                            text = stringResource(Res.string.chat_rate_limited),
                            isError = false,
                            onRetry = { viewModel.retryLast() },
                            onOpenSettings = onOpenSettings
                        )
                    }
                }
            }

            ChatComposer(
                input = state.input,
                baseAsset = baseAsset,
                ticker = state.ticker,
                // On an empty thread the catalogue above already offers everything, so the composer
                // carries chips only once there is an answer to react to.
                suggestions = if (state.messages.isEmpty()) emptyList() else state.suggestions,
                sendEnabled = !state.isAwaitingReply,
                onInputChange = viewModel::updateInput,
                onSend = { viewModel.send(state.input) },
                onSuggestionClick = viewModel::send
            )
        }
    }
}

/**
 * The pinned bottom block: chips, text field and disclaimer.
 *
 * Insets are the union of the navigation bar and the keyboard rather than `imePadding()` plus
 * `navigationBarsPadding()`, because the two overlap — the IME inset is measured from the bottom of
 * the screen and already covers the nav bar. Union takes the larger, which is correct whether the
 * keyboard is up or down, and whether the parent has consumed the bottom inset (Android, inside
 * `App()`'s Scaffold) or not (iOS 26, where each screen is its own Compose host).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatComposer(
    input: String,
    baseAsset: String,
    ticker: Ticker24hr?,
    suggestions: List<ChatSuggestion>,
    sendEnabled: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    // Chips are for when you don't know what to ask. Once the field has focus the user does, and
    // the keyboard has taken more than half the screen — a full set of chips in a bottom bar that
    // measures before the message list would squeeze the input itself off the screen.
    var inputFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (suggestions.isNotEmpty() && !inputFocused) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    suggestions.forEach { suggestion ->
                        val label = suggestionText(suggestion, baseAsset, ticker)
                        SuggestionChip(
                            onClick = { onSuggestionClick(label) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(16.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 140.dp)
                        .onFocusChanged { inputFocused = it.isFocused },
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.chat_input_hint, baseAsset),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.size(8.dp))

                val canSend = sendEnabled && input.isNotBlank()
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(Res.string.chat_send),
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            }

            Text(
                text = stringResource(Res.string.chat_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp)
            )
        }
    }
}

/**
 * A greeting rendered outside the transcript, so it is never sent to the model as a turn it
 * supposedly said.
 */
@Composable
private fun GreetingBubble(baseAsset: String) {
    BubbleRow(isUser = false) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 8.dp, top = 2.dp)
                    .size(18.dp)
            )
            Text(
                text = stringResource(Res.string.chat_greeting, baseAsset),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChatBubble(message: CoinChatMessage) {
    val isUser = message.role == ChatRole.User

    // Keyed on the text so a new answer starts from the un-copied state. A tick counter rather
    // than a Boolean, because writing `true` over `true` is a structural-equality no-op and the
    // effect would keep its old key — see AiInsightCard.
    var copyTick by remember(message.text) { mutableStateOf(0) }
    val copied = copyTick > 0
    LaunchedEffect(copyTick) {
        if (copyTick > 0) {
            delay(2000)
            copyTick = 0
        }
    }

    BubbleRow(isUser = isUser) {
        Column {
            SelectionContainer {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            copyToClipboard(message.text)
                            copyTick++
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = stringResource(Res.string.chat_copy),
                            modifier = Modifier.size(16.dp),
                            tint = if (copied) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    // Status in a live region rather than in the button's accessible name: a label
                    // swap on an already-focused control isn't announced.
                    if (copied) {
                        Text(
                            text = stringResource(Res.string.chat_copied),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "thinking")

    BubbleRow(isUser = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        // Offsets each dot's phase. A per-iteration `delayMillis` would not work:
                        // it applies to every cycle, so the dots would stay in lockstep.
                        initialStartOffset = StartOffset(index * 160)
                    ),
                    label = "dot$index"
                )
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.chat_thinking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** An inline failure or rate-limit notice, placed where the answer would have been. */
@Composable
private fun ChatNotice(
    text: String,
    isError: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    BubbleRow(isUser = false) {
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.chat_retry))
                }
                // Only offered where Settings is reachable from here.
                if (onOpenSettings != null) {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(Res.string.portfolio_open_settings))
                    }
                }
            }
        }
    }
}

/** Shared bubble chrome: side, shape, colour and the width cap. */
@Composable
private fun BubbleRow(isUser: Boolean, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = MaxBubbleWidth),
            shape = if (isUser) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
            },
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                content()
            }
        }
    }
}

/** One category of the browsable catalogue: a heading and its questions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionGroupSection(
    group: SuggestionGroup,
    baseAsset: String,
    ticker: Ticker24hr?,
    onClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(group.category.titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            group.suggestions.forEach { suggestion ->
                val label = suggestionText(suggestion, baseAsset, ticker)
                SuggestionChip(
                    onClick = { onClick(label) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(16.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * The localized wording for a chip — which is also the exact text sent as the question, so what the
 * user tapped is what appears in their transcript.
 *
 * Every question string takes the base asset; only [ChatSuggestion.WhyMoving] varies, needing the
 * size of the day's move and a different string depending on its direction.
 */
@Composable
internal fun suggestionText(
    suggestion: ChatSuggestion,
    baseAsset: String,
    ticker: Ticker24hr?
): String = if (suggestion == ChatSuggestion.WhyMoving) {
    val res = if (isMoveDown(ticker)) Res.string.chat_sug_why_down else Res.string.chat_sug_why_up
    stringResource(res, baseAsset, changeMagnitudeLabel(ticker))
} else {
    stringResource(suggestion.res, baseAsset)
}
