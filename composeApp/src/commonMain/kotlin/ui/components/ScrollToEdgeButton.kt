package ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.scroll_to_bottom
import utxo.composeapp.generated.resources.scroll_to_top

@Composable
fun BoxScope.ScrollToEdgeButton(
    listState: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val isNearTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 5 }
    }
    val visible by remember {
        derivedStateOf {
            totalItems > 10 && listState.layoutInfo.visibleItemsInfo.isNotEmpty()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = 24.dp, bottom = 16.dp),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    if (isNearTop) {
                        listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
                    } else {
                        listState.animateScrollToItem(0)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = if (isNearTop) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(
                    if (isNearTop) Res.string.scroll_to_bottom else Res.string.scroll_to_top
                )
            )
        }
    }
}
