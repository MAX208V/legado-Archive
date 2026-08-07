package io.legado.app.ui.book.bookmark

import android.graphics.Color

/**
 * 微信读书风格书签划线色板。
 * 存储值均为不透明 ARGB 实色（Int），绘制时由 [backgroundOf] 生成半透明高亮背景。
 */
object BookmarkColors {

    /** 橄榄黄（默认色） 0xFFD9C28C */
    const val YELLOW = -2506100

    /** 0xFF8BC34A */
    const val GREEN = -7617718

    /** 0xFF64B5F6 */
    const val BLUE = -10177034

    /** 0xFFE57373 */
    const val RED = -1739917

    /** 0xFFBA68C8 */
    const val PURPLE = -4560696

    const val DEFAULT = YELLOW

    val palette: List<Int> = listOf(YELLOW, GREEN, BLUE, RED, PURPLE)

    /** 划线高亮背景色（35% 透明度） */
    fun backgroundOf(color: Int): Int {
        return Color.argb(0x59, Color.red(color), Color.green(color), Color.blue(color))
    }
}