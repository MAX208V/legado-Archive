package io.legado.app.ui.dict.rule

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.widget.compose.AppManagementLazyColumn
import io.legado.app.ui.widget.compose.AppManagementListRow
import io.legado.app.ui.widget.compose.AppManagementMenuAction
import io.legado.app.ui.widget.compose.AppManagementPalette
import io.legado.app.ui.widget.compose.rememberAppManagementPalette
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun DictRuleScreen(
    rules: List<DictRule>,
    selectedNames: Set<String>,
    isSelectMode: Boolean,
    reorderEnabled: Boolean,
    onReorder: (List<DictRule>) -> Unit,
    onToggleSelection: (DictRule) -> Unit,
    onToggleEnabled: (DictRule, Boolean) -> Unit,
    onEdit: (DictRule) -> Unit,
    onDelete: (DictRule) -> Unit,
    onTest: (DictRule) -> Unit
) {
    val palette = rememberAppManagementPalette()
    val lazyListState = rememberLazyListState()
    val rulesSnapshot = rules.toList()
    val rulesSignature = rulesSnapshot.joinToString(separator = "\u001F") { it.toString() }
    var orderedRules by remember {
        mutableStateOf(rulesSnapshot, referentialEqualityPolicy())
    }
    LaunchedEffect(reorderEnabled, rulesSignature) {
        orderedRules = rulesSnapshot
    }
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        orderedRules = orderedRules.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    @Composable
    fun itemRow(rule: DictRule, dragHandle: (@Composable () -> Unit)? = null) {
        DictRuleItemRow(
            name = rule.name,
            enabled = rule.enabled,
            isSelected = rule.name in selectedNames,
            isSelectMode = isSelectMode,
            palette = palette,
            onToggleSelection = { onToggleSelection(rule) },
            onToggleEnabled = { enabled -> onToggleEnabled(rule, enabled) },
            onEdit = { onEdit(rule) },
            onDelete = { onDelete(rule) },
            onTest = { onTest(rule) },
            dragHandle = dragHandle
        )
    }

    AppManagementLazyColumn(
        palette = palette,
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        val displayedRules = if (reorderEnabled) orderedRules else rulesSnapshot
        items(
            items = displayedRules,
            key = { rule -> rule.name },
            contentType = { "dictRule" }
        ) { rule ->
            if (reorderEnabled) {
                ReorderableItem(reorderState, key = rule.name) {
                    itemRow(rule) {
                        Icon(
                            painter = painterResource(R.drawable.ic_drag_handle),
                            contentDescription = stringResource(R.string.sort),
                            tint = palette.settings.secondaryText,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(22.dp)
                                .draggableHandle(onDragStopped = { onReorder(orderedRules) })
                        )
                    }
                }
            } else {
                itemRow(rule)
            }
        }
    }
}

@Composable
private fun DictRuleItemRow(
    name: String,
    enabled: Boolean,
    isSelected: Boolean,
    isSelectMode: Boolean,
    palette: AppManagementPalette,
    onToggleSelection: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    dragHandle: (@Composable () -> Unit)? = null
) {
    AppManagementListRow(
        title = name,
        palette = palette,
        selected = isSelected,
        selectionVisible = isSelectMode,
        animatedSelection = true,
        reserveSelectionSlot = false,
        onToggleSelection = onToggleSelection,
        switchChecked = enabled,
        onSwitchChange = onToggleEnabled,
        titleMaxLines = 1,
        minHeight = 56.dp,
        drawPanelImage = false,
        onClick = {
            if (isSelectMode) onToggleSelection() else onEdit()
        },
        onLongClick = onToggleSelection,
        onEdit = onEdit,
        onDelete = onDelete,
        moreActions = listOf(
            AppManagementMenuAction(
                text = stringResource(R.string.test_dict_rule),
                onClick = onTest
            )
        ),
        leadingContent = dragHandle
    )
}
