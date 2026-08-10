package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
        // 每层严格 3 本书，横向均匀分布（space-between 近似 space-around），
        // 直接立在打通的长层板上；列数不受视图设置影响
        val columns = 3
        val cellWidth = (maxWidth - edgePadding * 2 - CellGap * (columns - 1)) / columns
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
        // 叠层：层板（下层，横向打通完整显示）→ 书（上层，底部距层板底边 Reveal，
        // 即封面底边=层板底部 12px 之上，接触阴影在 12px 空间内）
        Box(modifier = Modifier.fillMaxWidth()) {
            // 层板 wood_shelf：一条连续长木板，完整贯通整层宽度，厚度/倒角/下沿阴影完整可见
            androidx.compose.foundation.Image(
                painter = painterResource(woodShelfBoardRes(woodStyle)),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(BoardHeight)
            )
            // 书：每层固定 3 本，横向均匀分布，直接立在长层板上；
            // 封面 2:3 锁定直立；封面左侧书脊高光；书底接触阴影（投在层板表面）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = BoardReveal),
                horizontalArrangement = Arrangement.spacedBy(CellGap)
            ) {
                items.forEach { item ->
                    BookshelfGridItem(
                        item = item,
                        modifier = Modifier.width(cellWidth),
                        compactBottomSpace = true,
                        coverAspectRatio = CoverAspectRatio,
                        bottomShadow = true,
                        spineHighlight = true,
                        fragment = fragment,
                        lifecycle = lifecycle,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                }
            }
        }
        // 环境光遮蔽（ambient occlusion）：层板下方渐变阴影，形成层与层之间的深度
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AOHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent)
                    )
                )
        )
        // 行间缝隙：wood_gap 木纹背景露出（上层板条与下层封面顶的间隔）
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

/** 层板高度：按 wood_shelf 原图 460x150 比例完整显示（厚度/倒角/投影不压缩） */
private val BoardHeight = 48.dp

/** 层板下沿露出高度（用户：封面底边与层板底边保留 12px 间距 ≈ 4dp @3x，容纳接触阴影） */
private val BoardReveal = 4.dp

/** 行间缝隙（wood_gap 木纹背景露出，演出层间隔感） */
private val BoardGap = 10.dp

/** 封面宽高比锁定 2:3，直立摆放 */
private val CoverAspectRatio = 2f / 3f

/** 书之间的横向间距 */
private val CellGap = 12.dp

/** 层板下方环境光遮蔽高度 */
private val AOHeight = 8.dp

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other
