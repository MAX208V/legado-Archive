package io.legado.app.ui.book.bookmark

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.ComposeDialogFragment
import io.legado.app.ui.widget.compose.AppDialogSize
import io.legado.app.ui.widget.compose.LegadoComposeTheme
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.toMiuixPalette
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkDialog() : ComposeDialogFragment() {

    constructor(bookmark: Bookmark, editPos: Int = -1) : this() {
        arguments = Bundle().apply {
            putInt("editPos", editPos)
            putParcelable("bookmark", bookmark)
        }
    }

    /** 对话框关闭（保存/删除/取消）后触发，用于刷新阅读界面书签划线 */
    var onDismissCallback: (() -> Unit)? = null

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
        onDismissCallback = null
    }
    override val dialogSize: AppDialogSize = AppDialogSize.Form

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        @Suppress("DEPRECATION")
        val bookmark = arguments?.getParcelable<Bookmark>("bookmark")
        if (bookmark == null) {
            dismiss()
            return View(requireContext())
        }
        val editPos = arguments?.getInt("editPos", -1) ?: -1
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoComposeTheme {
                    BookmarkContent(
                        chapterName = bookmark.chapterName,
                        initialBookText = bookmark.bookText,
                        initialContent = bookmark.content,
                        initialColor = bookmark.color,
                        showDelete = editPos >= 0,
                        onSave = { bookText, content, color ->
                            bookmark.bookText = bookText
                            bookmark.content = content
                            bookmark.color = color
                            lifecycleScope.launch {
                                withContext(IO) { appDb.bookmarkDao.insert(bookmark) }
                                dismiss()
                            }
                        },
                        onDelete = {
                            lifecycleScope.launch {
                                withContext(IO) { appDb.bookmarkDao.delete(bookmark) }
                                dismiss()
                            }
                        },
                        onCancel = { dismiss() }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkContent(
    chapterName: String,
    initialBookText: String,
    initialContent: String,
    initialColor: Int,
    showDelete: Boolean,
    onSave: (String, String, Int) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val style = rememberAppDialogStyle()
    val palette = style.toMiuixPalette()
    var bookText by remember { mutableStateOf(initialBookText) }
    var content by remember { mutableStateOf(initialContent) }
    var color by remember { mutableStateOf(initialColor) }
    AppDialogFrame(
        title = chapterName.ifBlank { stringResource(R.string.bookmark) },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BookmarkField(
                    label = stringResource(R.string.bookmark),
                    value = bookText,
                    onValueChange = { bookText = it },
                    style = style
                )
                Spacer(modifier = Modifier.height(10.dp))
                BookmarkField(
                    label = stringResource(R.string.content),
                    value = content,
                    onValueChange = { content = it },
                    style = style
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BookmarkColors.palette.forEach { c ->
                        val isSelected = c == color
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = style.primaryText.copy(
                                        alpha = if (isSelected) 1f else 0.25f
                                    ),
                                    shape = CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        actions = {
            if (showDelete) {
                LegadoMiuixActionButton(
                    text = stringResource(R.string.delete),
                    palette = palette,
                    onClick = onDelete,
                    cornerRadius = style.actionRadius
                )
            }
            LegadoMiuixActionButton(
                text = stringResource(R.string.cancel),
                palette = palette,
                onClick = onCancel,
                cornerRadius = style.actionRadius
            )
            LegadoMiuixActionButton(
                text = stringResource(R.string.ok),
                palette = palette,
                onClick = { onSave(bookText, content, color) },
                primary = true,
                cornerRadius = style.actionRadius
            )
        }
    )
}

@Composable
private fun BookmarkField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    style: AppDialogStyle
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        shape = RoundedCornerShape(style.actionRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = style.primaryText,
            unfocusedTextColor = style.primaryText,
            focusedContainerColor = style.fieldSurface,
            unfocusedContainerColor = style.fieldSurface,
            cursorColor = style.accent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = style.accent,
            unfocusedLabelColor = style.secondaryText
        ),
        textStyle = LocalTextStyle.current.copy(
            color = style.primaryText,
            fontFamily = style.bodyFontFamily
        )
    )
}
