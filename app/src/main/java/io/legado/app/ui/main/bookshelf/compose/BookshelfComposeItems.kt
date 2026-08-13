package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.data.dao.BookShelfDisplay
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.rememberThemeUiPalette
import io.legado.app.lib.theme.titleTypeface
import io.legado.app.lib.theme.titleTextColor
import io.legado.app.utils.toTimeAgo

sealed interface BookshelfItemUi {
    val key: String
    val contentType: String
}

data class BookshelfFolderItemUi(
    val group: BookGroup
) : BookshelfItemUi {
    override val key: String = "folder:${group.groupId}"
    override val contentType: String = "folder"
}

data class BookshelfBookItemUi(
    val display: BookShelfDisplay,
    val isUpdating: Boolean,
    val unreadCount: Int,
    val hasNewChapter: Boolean,
    val tags: List<String>,
    val lastUpdateText: String?
) : BookshelfItemUi {
    override val key: String = "book:${display.bookUrl}"
    override val contentType: String = "book"
}

fun buildBookshelfItems(
    groups: List<BookGroup>,
    books: List<BookShelfDisplay>,
    isRootGroup: Boolean,
    groupId: Long,
    isUpdating: (String) -> Boolean
): List<BookshelfItemUi> {
    val configuredTags = AppConfig.bookshelfGroupTags[groupId].orEmpty()
    val hiddenTags = AppConfig.bookshelfHiddenTags[groupId].orEmpty()
    val bookItems = books.map { book ->
        BookshelfBookItemUi(
            display = book,
            isUpdating = !book.isLocal && isUpdating(book.bookUrl),
            unreadCount = book.getUnreadChapterNum(),
            hasNewChapter = book.lastCheckCount > 0,
            tags = book.displayUserTags(configuredTags, hiddenTags)
                .take(4),
            lastUpdateText = if (AppConfig.showLastUpdateTime && !book.isLocal) {
                book.latestChapterTime.toTimeAgo()
            } else {
                null
            }
        )
    }
    if (!isRootGroup) {
        return bookItems
    }
    return groups.map(::BookshelfFolderItemUi) + bookItems
}

private fun BookShelfDisplay.displayUserTags(
    configuredTags: List<String>,
    hiddenTags: Set<String>
): List<String> {
    val userTags = BookTagHelper.parse(customTag)
    val visibleConfiguredTags = configuredTags
        .filterNot { configured -> hiddenTags.any { it.equals(configured, ignoreCase = true) } }
    val candidateTags = visibleConfiguredTags.ifEmpty { userTags }
    return candidateTags.filter { candidate ->
        userTags.any { it.equals(candidate, ignoreCase = true) }
    }.distinctBy { it.lowercase() }
}

fun updateBookshelfItemUpdating(
    items: List<BookshelfItemUi>,
    bookUrl: String,
    isUpdating: (String) -> Boolean
): List<BookshelfItemUi> {
    var changed = false
    val updatedItems = items.map { item ->
        if (item is BookshelfBookItemUi && item.display.bookUrl == bookUrl) {
            val nextUpdating = !item.display.isLocal && isUpdating(bookUrl)
            if (nextUpdating != item.isUpdating) {
                changed = true
                item.copy(isUpdating = nextUpdating)
            } else {
                item
            }
        } else {
            item
        }
    }
    return if (changed) updatedItems else items
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookshelfGridItem(
    item: BookshelfItemUi,
    modifier: Modifier = Modifier,
    compactBottomSpace: Boolean = false,
    titleColorOverride: Color? = null,
    woodContactShadow: Boolean = false,
    woodSpineGlow: Boolean = false,
    woodCoverShade: Boolean = false,
    coverStyle: CoverImageView.CoverStyle = CoverImageView.CoverStyle.GRID,
    fragment: Fragment? = null,
    lifecycle: Lifecycle? = null,
    onClick: (BookshelfItemUi) -> Unit,
    onLongClick: (BookshelfItemUi) -> Unit
) {
    val context = LocalContext.current
    val themeSignature = rememberThemeUiPalette().signature
    val showBookName = AppConfig.showBookname
    val titleFontFamily = remember(context, themeSignature) {
        FontFamily(context.titleTypeface())
    }
    val titleColorArgb = context.titleTextColor
    val titleColor = remember(context, themeSignature, titleColorArgb) {
        Color(titleColorArgb)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
            .padding(
                start = 4.dp,
                top = 4.dp,
                end = 4.dp,
                bottom = if (compactBottomSpace) 0.dp else 4.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopEnd
        ) {
            BookshelfCover(
                item = item,
                modifier = Modifier.fillMaxWidth(),
                fillBounds = false,
                style = coverStyle,
                fragment = fragment,
                lifecycle = lifecycle
            )
            // 拟木书架：封面材质与仿真阴影（破除平贴感）
            if (woodCoverShade) {
                // 对角环境光：左上受光微亮 → 右下微暗（封面曲面感）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.06f)
                                ),
                                start = Offset.Zero,
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            )
                        )
                )
                // 右缘书脊暗部（书脊厚度）
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.42f),
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 书脊反射高光线（右缘暗带左侧细亮线，模拟书脊曲面反光）
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-6).dp)
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                            )
                        )
                )
                // 顶缘书顶暗部（补全四边立体厚度）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.24f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 底缘书底暗部
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.36f),
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 中央曲面高光（皮面圆柱反光，破除平贴）
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.40f)
                        .fillMaxHeight(0.96f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 右侧整条投影——书影投向右后方的墙/相邻书脊（纵深关键）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 2.dp)
                        .width(5.dp)
                        .fillMaxHeight(0.90f)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.26f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 纵深投影——书右下角被抬起，投影落在墙/隔条上（书浮于书架前）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                )
            }
            // 拟木书架：书底接触阴影（极淡，紧贴隔条处的微弱压暗）
            if (woodContactShadow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            // 拟木书架：书脊高光（左侧白色渐变反光）
            if (woodSpineGlow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.40f), Color.Transparent)
                            )
                        )
                )
            }
            if (item is BookshelfBookItemUi) {
                BookshelfStatusBadge(item)
            }
            if (showBookName == 2) {
                Text(
                    text = item.displayName,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = titleFontFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showBookName == 0) {
            Text(
                text = item.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = titleColorOverride ?: titleColor,
                fontSize = 12.sp,
                fontFamily = titleFontFamily,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BookshelfCover(
    item: BookshelfItemUi,
    modifier: Modifier,
    fragment: Fragment?,
    lifecycle: Lifecycle?,
    fillBounds: Boolean = false,
    style: CoverImageView.CoverStyle = CoverImageView.CoverStyle.GRID
) {
    BookshelfComposeCover(
        item = item,
        modifier = modifier,
        fragment = fragment,
        lifecycle = lifecycle,
        fillBounds = fillBounds,
        style = style
    )
}

@Composable
private fun BookshelfStatusBadge(item: BookshelfBookItemUi) {
    if (item.isUpdating) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(5.dp)
                .size(20.dp),
            strokeWidth = 2.dp,
            color = Color(LocalContext.current.accentColor)
        )
        return
    }
    if (!AppConfig.showUnread || item.unreadCount <= 0) return
    val badgeColor = if (item.hasNewChapter) {
        Color(LocalContext.current.accentColor)
    } else {
        Color.Black.copy(alpha = 0.55f)
    }
    Text(
        text = item.unreadCount.coerceAtMost(99999).toString(),
        modifier = Modifier
            .padding(5.dp)
            .clip(CircleShape)
            .background(badgeColor)
            .widthIn(min = 20.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

private val BookshelfItemUi.displayName: String
    get() = when (this) {
        is BookshelfBookItemUi -> display.name
        is BookshelfFolderItemUi -> group.groupName
    }
