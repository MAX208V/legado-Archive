package io.legado.app.ui.main.bookshelf.compose

import androidx.compose.ui.graphics.Color

/**
 * 拟物木制书架主题（纯代码配色，无位图依赖）。
 * 每种主题定义：木墙（背景）、层板（板面/前缘/高光）、支撑柱、横梁的整套色板。
 */
data class WoodShelfTheme(
    val nameRes: Int,
    val wallBase: Color,
    val wallDark: Color,
    val wallLight: Color,
    val plankTop: Color,
    val plankEdge: Color,
    val plankEdgeLight: Color,
    val column: Color,
    val columnHighlight: Color,
    val beam: Color,
    val beamHighlight: Color
) {
    companion object {
        val all: List<WoodShelfTheme> = listOf(
            // 柚木 Teak：金黄暖棕
            WoodShelfTheme(
                nameRes = io.legado.app.R.string.wood_style_teak,
                wallBase = Color(0xFF9A6B3F),
                wallDark = Color(0xFF7A4F2B),
                wallLight = Color(0xFFB8895A),
                plankTop = Color(0xFFA87A47),
                plankEdge = Color(0xFF8A5F33),
                plankEdgeLight = Color(0xFFC89A68),
                column = Color(0xFF6E4527),
                columnHighlight = Color(0xFFB0804E),
                beam = Color(0xFF7A522F),
                beamHighlight = Color(0xFFB0804E)
            ),
            // 胡桃 Walnut：深褐带紫
            WoodShelfTheme(
                nameRes = io.legado.app.R.string.wood_style_walnut,
                wallBase = Color(0xFF6B4A33),
                wallDark = Color(0xFF4E3423),
                wallLight = Color(0xFF83604A),
                plankTop = Color(0xFF75503A),
                plankEdge = Color(0xFF5C3D2A),
                plankEdgeLight = Color(0xFF977A63),
                column = Color(0xFF432E1F),
                columnHighlight = Color(0xFF7A5A42),
                beam = Color(0xFF4E3826),
                beamHighlight = Color(0xFF7A5A42)
            ),
            // 橡木 Oak：浅黄褐
            WoodShelfTheme(
                nameRes = io.legado.app.R.string.wood_style_oak,
                wallBase = Color(0xFFB9956B),
                wallDark = Color(0xFF8F6F4A),
                wallLight = Color(0xFFD0B088),
                plankTop = Color(0xFFC2A076),
                plankEdge = Color(0xFF9C7B54),
                plankEdgeLight = Color(0xFFE0C49A),
                column = Color(0xFF7E6140),
                columnHighlight = Color(0xFFB99A70),
                beam = Color(0xFF8A6B47),
                beamHighlight = Color(0xFFB99A70)
            ),
            // 红杉 Redwood：红棕
            WoodShelfTheme(
                nameRes = io.legado.app.R.string.wood_style_redwood,
                wallBase = Color(0xFF8C4A3A),
                wallDark = Color(0xFF6B3629),
                wallLight = Color(0xFFA96350),
                plankTop = Color(0xFF95513F),
                plankEdge = Color(0xFF743B2E),
                plankEdgeLight = Color(0xFFBC7A64),
                column = Color(0xFF5A2C22),
                columnHighlight = Color(0xFF965A44),
                beam = Color(0xFF632F24),
                beamHighlight = Color(0xFF965A44)
            ),
            // 黑檀 Ebony：近黑深棕
            WoodShelfTheme(
                nameRes = io.legado.app.R.string.wood_style_ebony,
                wallBase = Color(0xFF3E3229),
                wallDark = Color(0xFF2A211B),
                wallLight = Color(0xFF52453A),
                plankTop = Color(0xFF45382E),
                plankEdge = Color(0xFF2F261F),
                plankEdgeLight = Color(0xFF5E5044),
                column = Color(0xFF211A15),
                columnHighlight = Color(0xFF4A3D31),
                beam = Color(0xFF2A221C),
                beamHighlight = Color(0xFF4A3D31)
            )
        )

        fun of(index: Int): WoodShelfTheme =
            all[index.coerceIn(0, all.lastIndex)]
    }
}
