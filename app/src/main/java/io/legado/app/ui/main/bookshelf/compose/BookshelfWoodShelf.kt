package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.R

/**
 * 木制书架（模仿静读天下 Moon+ Reader 的木质书架）
 *
 * 结构：每一层 = 一排书封面"立"在一条木质层板上（书名底部紧贴层板），
 * 层板下方留一点缝隙再进入下一排书（封面顶部留缝）；整页背景为深色木墙。
 * 木色 0..4 对应 a(浅橡木)/b(胡桃木)/c(白蜡木)/d(深咖木)/e(黑檀木)。
 */
@Composable
fun BookshelfWoodShelfContent(
    items: List<BookshelfItemUi>,
    woodStyle: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier.fillMaxSize(),
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    fragment: Fragment? = null,
    lifecycle: Lifecycle? = null,
    onClick: (BookshelfItemUi) -> Unit,
    onLongClick: (BookshelfItemUi) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val edgePadding = 6.dp
        val minCellWidth = 74.dp
        // BookshelfGridItem 自带 4dp padding，行内视觉间距约为 8dp
        val cellGap = 8.dp
        val columns = (((maxWidth - edgePadding * 2 + cellGap) / (minCellWidth + cellGap))
            .toInt())
            .coerceAtLeast(1)
        val cellWidth = (maxWidth - edgePadding * 2 - cellGap * (columns - 1)) / columns
        val rows = items.chunked(columns)
        // 背景：整页铺亮色木板图（wood_gap，细腻木纹），书（封面+书名延伸）立在板上，
        // 板条贯通、行间空隙均露出木纹墙
        androidx.compose.foundation.Image(
            painter = painterResource(woodGapRes(woodStyle)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = edgePadding,
                top = contentTopPadding,
                end = edgePadding,
                bottom = contentBottomPadding
            )
        ) {
            itemsIndexed(
                items = rows,
                key = { index, row -> row.firstOrNull()?.key ?: "wood-row-$index" }
            ) { _, rowItems ->
                WoodShelfRow(
                    items = rowItems,
                    cellWidth = cellWidth,
                    woodStyle = woodStyle,
                    fragment = fragment,
                    lifecycle = lifecycle,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
    }
}

@Composable
private fun WoodShelfRow(
    items: List<BookshelfItemUi>,
    cellWidth: Dp,
    woodStyle: Int,
    fragment: Fragment?,
    lifecycle: Lifecycle?,
    onClick: (BookshelfItemUi) -> Unit,
    onLongClick: (BookshelfItemUi) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 书 = 封面 + 书名（书名是封面的延伸）；底部留 boardOverlap 空白，
        // 由板条上移覆盖，形成"书底伸入板条"的效果（书名完整可见）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = BoardOverlap),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items.forEach { item ->
                BookshelfGridItem(
                    item = item,
                    modifier = Modifier.width(cellWidth),
                    compactBottomSpace = true,
                    fragment = fragment,
                    lifecycle = lifecycle,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
        // 书架层板：完整横向贯通，上移覆盖书底 boardOverlap，下沿露出 boardReveal
        androidx.compose.foundation.Image(
            painter = painterResource(woodShelfBoardRes(woodStyle)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxWidth()
                .height(BoardHeight)
                .offset(y = (-BoardOverlap))
        )
        // 行间缝隙：wood_gap 木纹背景露出（封面顶部留一点缝隙）
        Spacer(modifier = Modifier.height(BoardGap))
    }
}

/** 书架层板资源：a/b/c/d/e 木色 */
internal fun woodShelfBoardRes(style: Int): Int = when (style.floorMod(5)) {
    0 -> R.drawable.wood_shelf_a
    1 -> R.drawable.wood_shelf_b
    2 -> R.drawable.wood_shelf_c
    3 -> R.drawable.wood_shelf_d
    else -> R.drawable.wood_shelf_e
}

/** 背景木墙资源（细腻木纹的亮木板，铺满整页）：a/b/c/d/e 木色 */
internal fun woodGapRes(style: Int): Int = when (style.floorMod(5)) {
    0 -> R.drawable.wood_gap_a
    1 -> R.drawable.wood_gap_b
    2 -> R.drawable.wood_gap_c
    3 -> R.drawable.wood_gap_d
    else -> R.drawable.wood_gap_e
}

/** 木制书架整页背景色调（深色木墙遮罩色，随木色变化） */
internal fun woodBackgroundColor(style: Int): Color = when (style.floorMod(5)) {
    0 -> Color(0xFF55402B)
    1 -> Color(0xFF473422)
    2 -> Color(0xFF7C7970)
    3 -> Color(0xFF232427)
    else -> Color(0xFF180F0C)
}

/** 层板高度 */
private val BoardHeight = 20.dp

/** 层板下沿露出高度（用户：封面底部在层板底部 12px 之上 ≈ 4dp @3x） */
private val BoardReveal = 4.dp

/** 书底伸入层板的覆盖量 */
private val BoardOverlap = BoardHeight - BoardReveal

/** 行间缝隙（wood_gap 木纹背景露出） */
private val BoardGap = 8.dp

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other
