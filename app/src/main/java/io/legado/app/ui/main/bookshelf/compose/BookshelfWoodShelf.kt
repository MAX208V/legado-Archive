package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.fragment.app.Fragment

/**
 * 拟物木制书架（Compose Canvas 纯代码绘制，无位图依赖）。
 *
 * 视觉构成：
 *  1. 固定木墙背景：竖向板条 + 木纹细线 + 板缝 + 噪点 + 顶部横梁 + 两侧支撑柱 + 底部底座
 *  2. 书架层（随内容滚动）：书本行（真实封面，高矮错落、底边对齐）+ 下层板（板面 + 前缘高光/倒角 + 底部暗边）
 *  3. 每层顶部上层板环境光遮蔽（AO），书底接触阴影
 */
@Composable
fun BookshelfWoodShelfContent(
    items: List<BookshelfItemUi>,
    woodStyle: Int,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentTopPadding: Dp,
    contentBottomPadding: Dp,
    fragment: Fragment?,
    lifecycle: Lifecycle?,
    onClick: (BookshelfItemUi) -> Unit,
    onLongClick: (BookshelfItemUi) -> Unit
) {
    val theme = remember(woodStyle) { WoodShelfTheme.of(woodStyle) }
    val chunks = remember(items) { items.chunked(3) }
    Box(modifier = modifier.fillMaxSize()) {
        // 固定木墙（不随内容滚动）
        WoodWallBackground(theme = theme, modifier = Modifier.fillMaxSize())
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentTopPadding,
                bottom = contentBottomPadding
            )
        ) {
            itemsIndexed(chunks) { index, chunk ->
                WoodShelfLayer(
                    books = chunk,
                    layerIndex = index,
                    theme = theme,
                    fragment = fragment,
                    lifecycle = lifecycle,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
    }
}

/** 一排书 + 下层板 */
@Composable
private fun WoodShelfLayer(
    books: List<BookshelfItemUi>,
    layerIndex: Int,
    theme: WoodShelfTheme,
    fragment: Fragment?,
    lifecycle: Lifecycle?,
    onClick: (BookshelfItemUi) -> Unit,
    onLongClick: (BookshelfItemUi) -> Unit
) {
    Column {
        // 上层板的投影（AO），压暗本层书顶，增强层叠感
        if (layerIndex > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AO_HEIGHT)
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent)
                    )
                )
            }
        }
        // 书本行：等宽直立（参考 iBooks 书架：书直接立在板面，无格无框）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SHELF_EDGE_PADDING)
                .padding(bottom = BOOK_BOARD_GAP),
            horizontalArrangement = Arrangement.spacedBy(BOOK_GAP),
            verticalAlignment = Alignment.Bottom
        ) {
            books.forEach { book ->
                BookshelfGridItem(
                    item = book,
                    modifier = Modifier.weight(1f),
                    compactBottomSpace = true,
                    woodContactShadow = true,
                    woodSpineGlow = true,
                    coverStyle = FLAT_COVER_STYLE,
                    fragment = fragment,
                    lifecycle = lifecycle,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            }
        }
        // 层板（随层滚动）
        WoodShelfBoard(theme = theme, modifier = Modifier.fillMaxWidth())
    }
}

/** 隔条：书底紧贴的深棕窄横条（参考 iBooks 书架参考图样式） */
@Composable
private fun WoodShelfBoard(theme: WoodShelfTheme, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val boardHeightPx = with(density) { BOARD_HEIGHT.toPx() }
    val facePx = with(density) { FACE_HEIGHT.toPx() }
    Canvas(modifier = modifier.height(BOARD_HEIGHT)) {
        // 板面：极薄的浅色过渡带（书底微亮）
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(theme.plankTop, theme.plankTop.copy(alpha = 0.8f))
            ),
            size = Size(size.width, facePx)
        )
        // 隔条主体：深棕木色（上缘略亮 → 深棕主体），横贯全宽
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    theme.plankEdge.copy(alpha = 0.35f),
                    theme.wallDark,
                    theme.wallDark
                )
            ),
            topLeft = Offset(0f, facePx),
            size = Size(size.width, boardHeightPx - facePx)
        )
        // 顶部 2px 高光（极其轻微）
        drawRect(
            color = theme.plankEdgeLight.copy(alpha = 0.30f),
            topLeft = Offset(0f, facePx),
            size = Size(size.width, EDGE_LIGHT_HEIGHT_PX)
        )
        // 底部暗边
        drawRect(
            color = Color.Black.copy(alpha = 0.30f),
            topLeft = Offset(0f, boardHeightPx - EDGE_DARK_HEIGHT),
            size = Size(size.width, EDGE_DARK_HEIGHT)
        )
    }
}

/** 固定木墙背景：板条 + 木纹 + 板缝 + 噪点（无装饰边框，纯木墙） */
@Composable
private fun WoodWallBackground(theme: WoodShelfTheme, modifier: Modifier = Modifier) {
    // 固定随机种子，避免滚动时木纹/噪点闪烁
    val grains = remember(theme) { WoodGrain.generate(theme, 42) }
    val density = LocalDensity.current
    val boardPx = with(density) { BOARD_WIDTH.toPx() }
    Canvas(modifier = modifier) {
        // 1. 木墙底色：上稍亮 → 下稍暗
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    theme.wallLight.copy(alpha = 0.92f),
                    theme.wallBase,
                    theme.wallDark.copy(alpha = 1.05f)
                )
            )
        )
        // 2. 板条木纹（固定随机）
        grains.forEach { g ->
            drawRect(
                color = g.color,
                topLeft = Offset(g.x, 0f),
                size = Size(g.width, size.height)
            )
        }
        // 3. 板缝（竖线）+ 缝右侧高光
        var x = 0f
        while (x < size.width) {
            drawRect(
                color = theme.wallDark.copy(alpha = 0.9f),
                topLeft = Offset(x, 0f),
                size = Size(SEAM_PX, size.height)
            )
            drawRect(
                color = theme.wallLight.copy(alpha = 0.25f),
                topLeft = Offset(x + SEAM_PX, 0f),
                size = Size(SEAM_HL_PX, size.height)
            )
            x += boardPx
        }
        // 4. 微噪点
        drawNoise(seed = grains)
    }
}

private fun DrawScope.drawNoise(seed: List<WoodGrain>) {
    val step = NOISE_STEP
    var y = 0f
    var i = 0
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            val g = seed[(i + x.toInt() * 7) % seed.size]
            drawRect(
                color = g.color.copy(alpha = g.color.alpha * 0.35f),
                topLeft = Offset(x, y),
                size = Size(1f, 1f)
            )
            i++
            x += step
        }
        y += step
    }
}

/** 木纹颗粒（固定生成一次，滚动不闪烁） */
private data class WoodGrain(val x: Float, val width: Float, val color: Color) {
    companion object {
        fun generate(theme: WoodShelfTheme, seed: Int): List<WoodGrain> {
            val list = ArrayList<WoodGrain>(160)
            var s = seed
            fun next(): Float {
                s = (s * 1103515245L + 12345).toInt()
                return ((s ushr 16) and 0x7FFF) / 32767f
            }
            repeat(140) {
                val dark = next() < 0.5f
                list.add(
                    WoodGrain(
                        x = next() * 2200f,
                        width = 1f + next() * 2f,
                        color = if (dark) {
                            theme.wallDark.copy(alpha = 0.05f + next() * 0.10f)
                        } else {
                            theme.wallLight.copy(alpha = 0.04f + next() * 0.08f)
                        }
                    )
                )
            }
            return list
        }
    }
}

// ---------- 尺寸常量 ----------
private val SHELF_EDGE_PADDING = 10.dp
private val BOOK_GAP = 10.dp
private val BOOK_BOARD_GAP = 2.dp     // 书底紧贴隔条
private val BOARD_HEIGHT = 12.dp     // 隔条高（深棕窄条）
private val FACE_HEIGHT = 2.dp       // 板面区高度（微亮过渡）
private val AO_HEIGHT = 12.dp
private val BOARD_WIDTH = 64.dp       // 木墙板条宽
private val SEAM_PX = 2f
private val SEAM_HL_PX = 2f
private val EDGE_LIGHT_HEIGHT_PX = 2f
private val EDGE_DARK_HEIGHT = 3f
private val NOISE_STEP = 22f
// 封面采用无外阴影风格（去掉卡片"格子"感，书直接立板）
private val FLAT_COVER_STYLE =
    io.legado.app.ui.widget.image.CoverImageView.CoverStyle.FLAT