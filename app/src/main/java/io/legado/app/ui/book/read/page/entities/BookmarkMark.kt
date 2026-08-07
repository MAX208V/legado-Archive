package io.legado.app.ui.book.read.page.entities

import io.legado.app.data.entities.Bookmark

/**
 * 书签在章节正文中的高亮区间
 * startPos/endPos 为章节内字符偏移（与 Bookmark.chapterPos 同坐标系，半开区间 [startPos, endPos)）
 */
data class BookmarkMark(
    val startPos: Int,
    val endPos: Int,
    val bookmark: Bookmark,
)

/**
 * 书签正文长度在此范围内视为"选中文字后添加的书签"，否则视为"位置书签"
 * （位置书签的 bookText 是全页文字，长度通常远大于此阈值）
 */
private const val SelectionMaxLen = 200

/** 用于区分"选中片段"的最短长度 */
private const val SelectionMinLen = 2

/** 位置书签退化的固定短划长度 */
private const val ShortMarkLen = 4

/**
 * 根据章节已排版文本 + 该书签列表，计算该章节所有书签的高亮区间。
 * 请勿在 UI 线程调用（会遍历整章节文本）。
 */
fun buildBookmarkMarks(chapter: TextChapter, bookmarks: List<Bookmark>): List<BookmarkMark> {
    if (bookmarks.isEmpty()) return emptyList()
    val content = chapter.getContent()
    val n = content.length
    if (n == 0) return emptyList()
    val marks = ArrayList<BookmarkMark>(bookmarks.size)
    for (bookmark in bookmarks) {
        val start = bookmark.chapterPos
        if (start < 0 || start >= n) continue
        val targetLen = bookmark.bookText.trim().length
        val end: Int = if (targetLen in SelectionMinLen..SelectionMaxLen) {
            val matched = matchLen(content, start, bookmark.bookText)
            if (matched >= SelectionMinLen) start + matched else shortEnd(start, n)
        } else {
            // 位置书签（bookText 为整页文字）→ 固定短划
            shortEnd(start, n)
        }
        if (end > start) {
            marks.add(BookmarkMark(start, end, bookmark))
        }
    }
    marks.sortBy { it.startPos }
    return marks
}

private fun shortEnd(start: Int, contentLen: Int): Int = minOf(start + ShortMarkLen, contentLen)

/**
 * 忽略空白差异地匹配 bookText，返回正文中被匹配到的长度；无法匹配返回 -1。
 */
private fun matchLen(content: String, start: Int, raw: String): Int {
    val target = raw.trim()
    if (target.isEmpty()) return -1
    val n = content.length
    var ci = start
    var ti = 0
    while (ti < target.length && ci < n) {
        val cc = content[ci]
        val tc = target[ti]
        if (cc == tc) {
            ci++
            ti++
            continue
        }
        val cw = isSpace(cc)
        val tw = isSpace(tc)
        if (cw || tw) {
            if (cw) ci++ else ti++
            continue
        }
        break
    }
    if (ti < target.length) return -1
    return ci - start
}

private fun isSpace(c: Char): Boolean {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u00A0' || c == '\u3000'
}