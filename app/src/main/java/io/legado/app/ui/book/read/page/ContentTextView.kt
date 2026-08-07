package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.PaperInkHelper
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.EpubFile
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.ui.book.bookmark.BookmarkColors
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.ReadSelectionPosition
import io.legado.app.ui.book.read.page.entities.BookmarkMark
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.ButtonColumn
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.ReviewColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.setHtml
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * 阅读内容视图
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private data class RenderSnapshot(
        val generation: Long,
        val pages: List<TextPage>
    )

    var selectAble = AppConfig.textSelectAble
    val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = ChapterProvider.visibleRect
    val selectStart = TextPos(0, -1, -1)
    private val selectEnd = TextPos(0, -1, -1)
    var textPage: TextPage = TextPage()
        private set
    private var pairedTextPage: TextPage? = null
    var isMainView = false
    var longScreenshot = false
    var reverseStartCursor = false
    var reverseEndCursor = false

    //滚动参数
    private val pageFactory get() = callBack.pageFactory
    private val pageDelegate get() = callBack.pageDelegate
    private var pageOffset = 0
    private var backgroundScrollOffset = 0
    private var scrollFollowBackgroundDrawable: ScrollFollowBackgroundDrawable? = null
    private var autoPager: AutoPager? = null
    private var isScroll = false
    private val renderPending = AtomicBoolean(false)
    private val renderGeneration = AtomicLong(0L)
    private val pendingRenderSnapshot = AtomicReference<RenderSnapshot?>(null)
    private var lastClickTime = 0L
    private var doubleClick = false
    private var nativeSelectedText: String? = null
    private var nativeSelectionRect: RectF? = null
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    //书签划线数据与画笔
    private val bookmarkMarks = hashMapOf<Int, List<BookmarkMark>>()

    private val bookmarkMarkPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
            color = context.getCompatColor(R.color.bookmark_highlight_bg)
        }
    }

    private val bookmarkUnderlinePaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
            color = context.getCompatColor(R.color.bookmark_highlight_underline)
        }
    }

    //绘制图片的paint
    val imagePaint by lazy {
        Paint().apply {
            isAntiAlias = AppConfig.useAntiAlias
        }
    }

    init {
        callBack = activity as CallBack
    }

    /**
     * 设置内容
     */
    fun setContent(
        textPage: TextPage,
        pairedTextPage: TextPage? = null,
        resetBackgroundOffset: Boolean = true
    ) {
        if (this.textPage !== textPage || this.pairedTextPage !== pairedTextPage) {
            nativeSelectedText = null
            nativeSelectionRect = null
        }
        this.textPage = textPage
        this.pairedTextPage = pairedTextPage
        if (resetBackgroundOffset) {
            backgroundScrollOffset = 0
        }
        if (isScroll) {
            postInvalidate()
        } else {
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isMainView) return
        ChapterProvider.upViewSize(w, h)
        if (!textPage.isNativeEpubPage()) {
            textPage.format()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        autoPager?.onDraw(canvas)
        if (longScreenshot) {
            canvas.translate(0f, scrollY.toFloat())
        }
        drawScrollFollowBackground(canvas)
        drawPaperEffect(canvas)
        check(!visibleRect.isEmpty) { "visibleRect 为空" }
        if (!textPage.hasEpubBackground()) {
            canvas.clipRect(visibleRect)
        }
        drawPage(canvas)
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        val pairedPage = pairedTextPage
        if (!callBack.isScroll && ChapterProvider.doublePage) {
            val halfWidth = width / 2f
            drawPageInBounds(canvas, textPage, 0f, relativeOffset, 0f, halfWidth)
            pairedPage?.let {
                drawPageInBounds(canvas, it, halfWidth, relativeOffset, halfWidth, width.toFloat())
            }
        } else {
            if (!callBack.isScroll || pageIntersectsViewport(relativeOffset, textPage.height)) {
                textPage.draw(this, canvas, relativeOffset)
            }
        }
        if (callBack.isScroll) {
            if (!pageFactory.hasNext()) {
                nativeSelectionRect?.let { rect -> drawSelectedRect(canvas, rect) }
                return
            }
            val textPage1 = relativePage(1)
            relativeOffset += textPage.height
            if (pageIntersectsViewport(relativeOffset, textPage1.height)) {
                textPage1.draw(this, canvas, relativeOffset)
            }
            if (pageFactory.hasNextPlus()) {
                relativeOffset += textPage1.height
                val textPage2 = relativePage(2)
                if (pageIntersectsViewport(relativeOffset, textPage2.height)) {
                    textPage2.draw(this, canvas, relativeOffset)
                }
            }
        }
        nativeSelectionRect?.let { rect -> drawSelectedRect(canvas, rect) }
    }

    private fun pageIntersectsViewport(offset: Float, pageHeight: Float): Boolean {
        return offset < visibleRect.bottom && offset + pageHeight > visibleRect.top
    }

    fun drawSelectedRect(canvas: Canvas, rect: RectF) {
        canvas.drawRect(rect, selectedPaint)
    }

    fun drawSelectedRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        canvas.drawRect(left, top, right, bottom, selectedPaint)
    }

    private fun drawPageInBounds(
        canvas: Canvas,
        page: TextPage,
        translateX: Float,
        relativeOffset: Float,
        clipLeft: Float,
        clipRight: Float
    ) {
        canvas.save()
        canvas.clipRect(clipLeft, 0f, clipRight, height.toFloat())
        canvas.translate(translateX, 0f)
        page.draw(this, canvas, relativeOffset)
        canvas.restore()
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
        autoPager?.computeOffset()
    }

    override fun onDetachedFromWindow() {
        // Render requests capture TextPage instances and this View. Invalidate their generation
        // before detaching so queued work cannot post a stale reader update.
        renderGeneration.incrementAndGet()
        pendingRenderSnapshot.set(null)
        super.onDetachedFromWindow()
    }

    /**
     * 滚动事件
     * pageOffset 向上滚动 减小 向下滚动 增大
     * pageOffset 范围 0 ~ -textPage.height 大于0为上一页，小于-textPage.height为下一页
     * 以内容显示区域顶端为界，pageOffset的绝对值为textPage上方的高度
     * pageOffset + textPage.height 为 textPage 下方的高度
     */
    fun scroll(mOffset: Int) {
        val startPageOffset = pageOffset
        var backgroundDelta = mOffset
        pageOffset += mOffset
        if (longScreenshot) {
            scrollY += -mOffset
        }
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0
            backgroundDelta = pageOffset - startPageOffset
            pageDelegate?.abortAnim()
        } else if (!pageFactory.hasNext()
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = (ChapterProvider.visibleHeight - textPage.height).toInt()
            pageOffset = min(0, offset)
            backgroundDelta = pageOffset - startPageOffset
            pageDelegate?.abortAnim()
        } else if (pageOffset > 0) {
            if (pageFactory.moveToPrev(true)) {
                pageOffset -= textPage.height.toInt()
            } else {
                pageOffset = 0
                backgroundDelta = pageOffset - startPageOffset
                pageDelegate?.abortAnim()
            }
        } else if (pageOffset < -textPage.height) {
            val height = textPage.height
            if (pageFactory.moveToNext(upContent = true)) {
                pageOffset += height.toInt()
            } else {
                pageOffset = -height.toInt()
                backgroundDelta = pageOffset - startPageOffset
                pageDelegate?.abortAnim()
            }
        }
        backgroundScrollOffset += backgroundDelta
        postInvalidateOnAnimation()
    }

    fun submitRenderTask() {
        val generation = renderGeneration.incrementAndGet()
        pendingRenderSnapshot.set(captureRenderSnapshot(generation))
        scheduleRenderTask()
    }

    private fun captureRenderSnapshot(generation: Long): RenderSnapshot {
        val pages = ArrayList<TextPage>(4)
        fun addPage(page: TextPage) {
            if (pages.none { it === page }) pages.add(page)
        }
        pageFactory.run {
            if (hasPrev()) addPage(prevPage)
            addPage(curPage)
            if (isScroll && hasNext()) addPage(nextPage)
            if (isScroll && hasNextPlus() && relativeOffset(2) < ChapterProvider.visibleHeight) {
                addPage(nextPlusPage)
            }
        }
        return RenderSnapshot(generation, pages)
    }

    private fun scheduleRenderTask() {
        if (!renderPending.compareAndSet(false, true)) return
        renderThread.submit {
            try {
                while (true) {
                    val snapshot = pendingRenderSnapshot.getAndSet(null) ?: break
                    var invalidate = false
                    for (page in snapshot.pages) {
                        if (snapshot.generation != renderGeneration.get()) break
                        invalidate = page.render(this) || invalidate
                        if (snapshot.generation != renderGeneration.get()) break
                    }
                    if (invalidate && snapshot.generation == renderGeneration.get()) {
                        post {
                            if (snapshot.generation == renderGeneration.get()) {
                                invalidate()
                                pageDelegate?.postInvalidate()
                            }
                        }
                    }
                }
            } finally {
                renderPending.set(false)
                if (pendingRenderSnapshot.get() != null) scheduleRenderTask()
            }
        }
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        pageOffset = 0
        backgroundScrollOffset = 0
        invalidateBackgroundHost()
    }

    fun getBackgroundOffset(): Int {
        return backgroundScrollOffset
    }

    fun setScrollFollowBackground(bitmap: Bitmap?, alpha: Int) {
        scrollFollowBackgroundDrawable = bitmap?.takeUnless { it.isRecycled }?.let {
            ScrollFollowBackgroundDrawable(it) { getBackgroundOffset() }.apply {
                setAlpha(alpha)
            }
        }
        postInvalidate()
    }

    fun setScrollFollowBackgroundAlpha(alpha: Int) {
        scrollFollowBackgroundDrawable?.setAlpha(alpha)
        postInvalidate()
    }

    private fun invalidateBackgroundHost() {
        postInvalidateOnAnimation()
    }

    private fun drawScrollFollowBackground(canvas: Canvas) {
        scrollFollowBackgroundDrawable?.let {
            it.setBounds(0, 0, width, height)
            it.draw(canvas)
        }
    }

    private fun drawPaperEffect(canvas: Canvas) {
        PaperInkHelper.drawBackground(canvas, width, height, paperPaint)
    }

    fun drawTextWithPaperInk(
        canvas: Canvas,
        text: String,
        start: Int,
        end: Int,
        x: Float,
        y: Float,
        paint: Paint,
        enableBlend: Boolean = true
    ) {
        PaperInkHelper.drawText(canvas, text, start, end, x, y, paint, enableBlend)
    }

    fun drawTextWithPaperInk(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        enableBlend: Boolean = true
    ) {
        drawTextWithPaperInk(canvas, text, 0, text.length, x, y, paint, enableBlend)
    }

    /**
     * 长按
     */
    fun longPress(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ): Boolean {
        if (isNativeEpubHit(x, y)) {
            return true
        }
        var handled = false
        touch(x, y) { _, textPos, _, textLine, column ->
            when (column) {
                is ImageColumn -> callBack.onImageLongPress(
                    x = x,
                    y = y,
                    src = column.src,
                    paragraphNum = textLine.paragraphNum,
                    imageIndexInParagraph = imageIndexInParagraph(textLine, column)
                )
                is TextColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                    handled = true
                }
                is TextHtmlColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                    handled = true
                }
            }
        }
        return handled
    }

    private fun imageIndexInParagraph(textLine: TextLine, target: ImageColumn): Int {
        if (textLine.paragraphNum <= 0) return 0
        var index = 0
        val paragraph = textLine.textPage.textChapter
            .getParagraphs(pageSplit = false)
            .firstOrNull { it.realNum == textLine.paragraphNum }
            ?: return 0
        paragraph.textLines.forEach { line ->
            line.columns.forEach { column ->
                if (column is ImageColumn && column.src == target.src) {
                    if (column === target) return index
                    index++
                }
            }
        }
        return 0
    }

    /**
     * 单击
     * @return true:已处理, false:未处理
     */
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun click(x: Float, y: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        val debounceClick = currentTime - lastClickTime < 300L //300毫秒防抖和双击
        lastClickTime = currentTime
        doubleClick = if (debounceClick) {
            !doubleClick
        } else {
            false
        }
        handleEpubNoteClick(x, y)?.let { return it }
        //书签划线：点击命中划线区域时弹出预览
        findBookmarkMarkAt(x, y)?.let {
            showBookmarkPreview(it, x, y)
            return true
        }
        var handled = false
        touch(x, y) { _, textPos, textPage, textLine, column ->
            when (column) {
                is ButtonColumn -> {
                    context.toastOnUi(R.string.epub_button_pressed)
                    handled = true
                }

                is ReviewColumn -> {
                    context.toastOnUi(R.string.epub_button_pressed)
                    handled = true
                }

                is ImageColumn -> when (AppConfig.clickImgWay) {
                    "1" -> { //预览图片
                        activity?.showDialogFragment(PhotoDialog(column.src, isBook = true))
                        handled = true
                    }
                    "2" -> { //兼容处理
                        if (!debounceClick) {
                            if (ReadBook.book?.isOnLineTxt == true) {
                                val click = column.click
                                val src = column.src
                                if (!click.isNullOrBlank()) {
                                    callBack.clickImg(click, src)
                                    handled = true
                                } else {
                                    handled = callBack.oldClickImg(src)
                                }
                            }
                        }
                    }
                    "3" -> { //关闭
                        handled = false
                    }
                    "4" -> { //双击
                        if (doubleClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        } else {
                            handled = true
                        }
                    }
                    else -> { //默认点击
                        if (!debounceClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        }
                    }
                }
                is TextHtmlColumn -> {
                    column.linkUrl?.let {
                        if (it.startsWith(EPUB_MEDIA_LINK_PREFIX)) {
                            context.toastOnUi(R.string.epub_media_not_supported)
                        } else {
                            activity?.startActivity<OpenUrlConfirmActivity> {
                                putExtra("uri", it)
                            }
                        }
                        handled = true
                    }
                }
            }
        }
        return handled
    }

    private fun handleEpubNoteClick(x: Float, y: Float): Boolean? {
        val book = ReadBook.book ?: return null
        for (relativePos in 0..lastRelativePageIndex()) {
            if (!isInRelativePage(x, relativePos)) continue
            val offset = relativeOffset(relativePos)
            if (relativePos > 0 && callBack.isScroll && offset >= ChapterProvider.visibleHeight) break
            val page = relativePage(relativePos)
            val localX = x - pageHorizontalOffset(relativePos)
            val href = page.findEpubLinkAt(localX, y - offset) ?: continue
            AppLog.put("EPUB Footnote click hit: href=$href, x=$x, y=${y - offset}, pageLinks=${page.epubLinkDiagnostics()}")
            if (!href.contains("#")) return null
            showEpubFootnote(book, href)
            return true
        }
        val page = relativePage(0)
        if (page.isNativeEpubPage()) {
            AppLog.put("EPUB Footnote click miss: x=$x, y=$y, pageLinks=${page.epubLinkDiagnostics()}")
        }
        return null
    }

    private fun showEpubFootnote(book: Book, href: String) {
        footnoteThread.execute {
            val note = runCatching {
                EpubFile.getFootnote(book, href)
            }.getOrNull()
            post {
                if (note == null) {
                    AppLog.put("EPUB Footnote resolve failed: href=$href")
                    context.toastOnUi(R.string.epub_footnote_load_failed)
                } else {
                    val content = note.html
                    activity?.showDialogFragment(TextDialog(note.title, content, TextDialog.Mode.HTML))
                }
            }
        }
    }

    /**
     * 选择文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touchRough(x, y) { _, textPos, _, _, column ->
            if (column is TextBaseColumn) {
                column.selected = true
                select(textPos)
            }
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (selectStart.compare(textPos) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectEnd) <= 0) {
                selectStartMoveIndex(textPos)
            } else {
                touchRough(x - 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectEnd) > 0) {
                        reverseStartCursor = true
                        reverseEndCursor = false
                        selectEnd.columnIndex++
                        selectStartMoveIndex(selectEnd)
                        selectEndMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (textPos.compare(selectEnd) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectStart) >= 0) {
                selectEndMoveIndex(textPos)
            } else {
                touchRough(x + 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectStart) < 0) {
                        reverseEndCursor = true
                        reverseStartCursor = false
                        selectStart.columnIndex--
                        selectEndMoveIndex(selectStart)
                        selectStartMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * @param touched 回调
     */
    private fun touch(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        if (!visibleRect.contains(x, y)) return
        for (relativePos in 0..lastRelativePageIndex()) {
            if (!isInRelativePage(x, relativePos)) continue
            val relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0 && callBack.isScroll && relativeOffset >= ChapterProvider.visibleHeight) return
            val localX = x - pageHorizontalOffset(relativePos)
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                if (textLine.isTouch(localX, y, relativeOffset)) {
                    for ((charIndex, textColumn) in textLine.columns.withIndex()) {
                        if (textColumn.isTouch(localX)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    private fun touchRough(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        for (relativePos in 0..lastRelativePageIndex()) {
            if (!isInRelativePage(x, relativePos)) continue
            val relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0 && callBack.isScroll && relativeOffset >= ChapterProvider.visibleHeight) return
            val localX = x - pageHorizontalOffset(relativePos)
            val textPage = relativePage(relativePos)
            for (lineIndex in textPage.lines.indices) {
                val textLine = textPage.getLine(lineIndex)
                if (textLine.isTouchY(y, relativeOffset)) {
                    val columns = textLine.columns
                    for (charIndex in columns.indices) {
                        val textColumn = columns[charIndex]
                        if (textColumn.isTouch(localX)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    val isLast = columns.first().start < localX
                    val charIndex = if (isLast) columns.lastIndex + 1 else -1
                    val textColumn = if (isLast) columns.last() else columns.first()
                    touched.invoke(
                        relativeOffset,
                        TextPos(relativePos, lineIndex, charIndex),
                        textPage, textLine, textColumn
                    )
                    return
                }
            }
        }
    }

    fun getCurVisiblePage(): TextPage {
        val visiblePage = TextPage()
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    visiblePage.addLine(visibleLine)
                }
            }
        }
        return visiblePage
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    return textPage.chapterIndex to visibleLine
                }
            }
        }
        return null
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectStart.relativePagePos = relativePagePos
        selectStart.lineIndex = lineIndex
        selectStart.columnIndex = max(0, charIndex)
        val textLine = relativePage(relativePagePos).getLine(lineIndex)
        val textColumn = textLine.getColumn(charIndex)
        val offsetX = pageHorizontalOffset(relativePagePos)
        upSelectedStart(
            offsetX + if (charIndex < textLine.columns.size) textColumn.start else textColumn.end,
            textLine.lineBottom + relativeOffset(relativePagePos),
            textLine.lineTop + relativeOffset(relativePagePos)
        )
        upSelectChars()
    }

    fun selectStartMoveIndex(textPos: TextPos) = textPos.run {
        selectStartMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(
        relativePage: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectEnd.relativePagePos = relativePage
        selectEnd.lineIndex = lineIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        selectEnd.columnIndex = min(charIndex, textLine.columns.lastIndex)
        val textColumn = textLine.getColumn(charIndex)
        val offsetX = pageHorizontalOffset(relativePage)
        upSelectedEnd(
            offsetX + if (charIndex > -1) textColumn.end else textColumn.start,
            textLine.lineBottom + relativeOffset(relativePage)
        )
        upSelectChars()
    }

    fun selectEndMoveIndex(textPos: TextPos) = textPos.run {
        selectEndMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    private fun upSelectChars() {
        if (!selectStart.isSelected() && !selectEnd.isSelected()) {
            return
        }
        val last = lastRelativePageIndex()
        val textPos = TextPos(0, 0, 0)
        for (relativePos in 0..last) {
            textPos.relativePagePos = relativePos
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                textPos.lineIndex = lineIndex
                for ((charIndex, column) in textLine.columns.withIndex()) {
                    textPos.columnIndex = charIndex
                    if (column is TextBaseColumn) {
                        val compareStart = textPos.compare(selectStart)
                        val compareEnd = textPos.compare(selectEnd)
                        column.selected = compareStart >= 0 && compareEnd <= 0
                        column.isSearchResult =
                            column.selected && callBack.isSelectingSearchResult
                        if (column.isSearchResult) {
                            textPage.searchResult.add(column)
                        }
                    }
                }
            }
        }
        postInvalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) {
        callBack.run {
            upSelectedStart(x + imgBgPaddingStart, y + headerHeight, top + headerHeight)
        }
    }

    private fun upSelectedEnd(x: Float, y: Float) {
        callBack.run {
            upSelectedEnd(x + imgBgPaddingStart, y + headerHeight)
        }
    }

    fun resetReverseCursor() {
        reverseStartCursor = false
        reverseEndCursor = false
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        nativeSelectedText = null
        nativeSelectionRect = null
        val last = lastRelativePageIndex()
        for (relativePos in 0..last) {
            val textPage = relativePage(relativePos)
            textPage.lines.forEach { textLine ->
                textLine.columns.forEach {
                    if (it is TextBaseColumn) {
                        it.selected = false
                        if (clearSearchResult) {
                            it.isSearchResult = false
                            textPage.searchResult.remove(it)
                        }
                    }
                }
            }
        }
        selectStart.reset()
        selectEnd.reset()
        postInvalidate()
        callBack.onCancelSelect()
    }

    fun getSelectedText(): String {
        nativeSelectedText?.takeIf { it.isNotBlank() }?.let { return it }
        val textPos = TextPos(0, 0, 0)
        val builder = StringBuilder()
        for (relativePos in selectStart.relativePagePos..selectEnd.relativePagePos) {
            val textPage = relativePage(relativePos)
            textPos.relativePagePos = relativePos
            textPage.lines.forEachIndexed { lineIndex, textLine ->
                textPos.lineIndex = lineIndex
                textLine.columns.forEachIndexed { charIndex, column ->
                    textPos.columnIndex = charIndex
                    val compareStart = textPos.compare(selectStart)
                    val compareEnd = textPos.compare(selectEnd)
                    if (column is TextBaseColumn) {
                        when {
                            compareStart == -1 -> if (
                                selectStart.columnIndex == textLine.columns.size
                                && charIndex == textLine.columns.lastIndex
                            ) {
                                builder.append("\n")
                            }

                            compareEnd == 1 -> if (selectEnd.columnIndex == -1 && charIndex == 0) {
                                builder.append("\n")
                            }

                            compareStart >= 0 && compareEnd <= 0 -> {
                                builder.append(column.charData)
                                if (
                                    textLine.isParagraphEnd
                                    && charIndex == textLine.columns.lastIndex
                                    && compareEnd != 0
                                ) {
                                    builder.append("\n")
                                }
                            }
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    fun hasSelection(): Boolean {
        return !nativeSelectedText.isNullOrBlank() || (selectStart.isSelected() && selectEnd.isSelected())
    }

    fun hasNativeSelection(): Boolean = !nativeSelectedText.isNullOrBlank()

    fun getSelectedReadPosition(): ReadSelectionPosition? {
        if (hasNativeSelection() || !selectStart.isSelected()) return null
        val bookUrl = ReadBook.book?.bookUrl ?: return null
        return runCatching {
            val page = relativePage(selectStart.relativePagePos)
            val chapter = page.getTextChapter()
            val pagePosition = page.getPosByLineColumn(
                selectStart.lineIndex,
                selectStart.columnIndex
            )
            ReadSelectionPosition(
                bookUrl = bookUrl,
                chapterIndex = page.chapterIndex,
                chapterUrl = chapter.chapter.url,
                chapterPosition = chapter.getReadLength(page.index) + pagePosition
            )
        }.getOrNull()
    }

    private fun isNativeEpubHit(x: Float, y: Float): Boolean {
        val last = lastRelativePageIndex()
        for (relativePos in 0..last) {
            val page = relativePage(relativePos)
            if (!page.isNativeEpubPage()) continue
            if (!isInRelativePage(x, relativePos)) continue
            val offset = relativeOffset(relativePos)
            val localY = y - offset
            val localX = x - pageHorizontalOffset(relativePos)
            val href = page.findEpubLinkAt(localX, localY)
            if (href != null) {
                return false
            }
            if (page.findNativeTextSelectionAt(localX, localY) != null) {
                nativeSelectedText = null
                nativeSelectionRect = null
                postInvalidate()
                return true
            }
        }
        return false
    }

    private fun selectNativeText(x: Float, y: Float): String? {
        val last = lastRelativePageIndex()
        for (relativePos in 0..last) {
            val page = relativePage(relativePos)
            if (!page.isNativeEpubPage()) continue
            if (!isInRelativePage(x, relativePos)) continue
            val offset = relativeOffset(relativePos)
            val localY = y - offset
            val localX = x - pageHorizontalOffset(relativePos)
            val selection = page.findNativeTextSelectionAt(localX, localY) ?: continue
            val hitRect = RectF(
                pageHorizontalOffset(relativePos) + selection.rect.left + page.epubDrawOffsetX,
                selection.rect.top + page.epubDrawOffsetY + offset,
                pageHorizontalOffset(relativePos) + selection.rect.right + page.epubDrawOffsetX,
                selection.rect.bottom + page.epubDrawOffsetY + offset
            )
            nativeSelectedText = selection.expandedText ?: selection.text
            nativeSelectionRect = hitRect
            postInvalidate()
            upSelectedStart(hitRect.left, hitRect.bottom, hitRect.top)
            upSelectedEnd(hitRect.right, hitRect.bottom)
            return selection.text
        }
        return null
    }

    fun createBookmark(): Bookmark? {
        val page = relativePage(selectStart.relativePagePos)
        page.getTextChapter().let { chapter ->
            ReadBook.book?.let { book ->
                return book.createBookMark().apply {
                    chapterIndex = page.chapterIndex
                    chapterPos = chapter.getReadLength(page.index) +
                            page.getPosByLineColumn(selectStart.lineIndex, selectStart.columnIndex)
                    chapterName = chapter.title
                    bookText = getSelectedText()
                }
            }
        }
        return null
    }

    private fun lastRelativePageIndex(): Int {
        return when {
            callBack.isScroll -> 2
            ChapterProvider.doublePage -> 1
            else -> 0
        }
    }

    //region 书签划线（微信读书风格）

    fun getBookmarkMarks(chapterIndex: Int): List<BookmarkMark> {
        return bookmarkMarks[chapterIndex] ?: emptyList()
    }

    fun setBookmarkMarks(chapterIndex: Int, marks: List<BookmarkMark>) {
        bookmarkMarks[chapterIndex] = marks
        postInvalidate()
    }

    fun clearBookmarkMarks() {
        bookmarkMarks.clear()
        postInvalidate()
    }

    /**
     * 绘制书签划线背景（文字下方，需在文字绘制前调用）
     */
    fun drawBookmarkMarkRect(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int
    ) {
        if (right <= left) return
        bookmarkMarkPaint.color = BookmarkColors.backgroundOf(color)
        canvas.drawRect(left, top, right, bottom, bookmarkMarkPaint)
    }

    /**
     * 绘制书签下划线
     */
    fun drawBookmarkUnderline(
        canvas: Canvas,
        left: Float,
        baselineY: Float,
        right: Float,
        color: Int
    ) {
        if (right <= left) return
        bookmarkUnderlinePaint.color = color
        val h = 2.dpToPx().toFloat()
        canvas.drawRect(left, baselineY, right, baselineY + h, bookmarkUnderlinePaint)
    }

    /**
     * 命中检测：返回 (x, y) 点击位置命中的书签划线
     */
    private fun findBookmarkMarkAt(x: Float, y: Float): BookmarkMark? {
        if (!visibleRect.contains(x, y)) return null
        for (relativePos in 0..lastRelativePageIndex()) {
            if (!isInRelativePage(x, relativePos)) continue
            val offset = relativeOffset(relativePos)
            if (relativePos > 0 && callBack.isScroll && offset >= ChapterProvider.visibleHeight) break
            val page = relativePage(relativePos)
            val marks = bookmarkMarks[page.chapterIndex] ?: continue
            if (marks.isEmpty()) continue
            val localX = x - pageHorizontalOffset(relativePos)
            for (line in page.lines) {
                if (!line.isTouch(localX, y, offset)) continue
                val lineStartPos = line.chapterPosition
                var charOffset = 0
                for (column in line.columns) {
                    if (column !is TextBaseColumn) continue
                    if (column.isTouch(localX)) {
                        val absStart = lineStartPos + charOffset
                        val absEnd = absStart + column.charData.length
                        return marks.firstOrNull { it.startPos < absEnd && it.endPos > absStart }
                    }
                    charOffset += column.charData.length
                }
                return null
            }
        }
        return null
    }

    /**
     * 在点击位置附近弹出书签预览气泡
     */
    private fun showBookmarkPreview(mark: BookmarkMark, x: Float, y: Float) {
        val ctx = context
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_surface)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = 8 * density
        }
        val bookmarkText = mark.bookmark.bookText.trim()
        val note = mark.bookmark.content.trim()
        if (bookmarkText.isNotEmpty()) {
            card.addView(TextView(ctx).apply {
                text = bookmarkText
                setTextColor(ctx.getCompatColor(R.color.primaryText))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.15f)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        if (note.isNotEmpty()) {
            card.addView(TextView(ctx).apply {
                text = note
                setTextColor(ctx.getCompatColor(R.color.secondaryText))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(6), 0, 0)
            })
        }
        if (bookmarkText.isEmpty() && note.isEmpty()) {
            card.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.bookmark)
                setTextColor(ctx.getCompatColor(R.color.secondaryText))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }
        //操作行
        val buttonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        var popup: PopupWindow? = null
        val circleSize = dp(24)
        //划线颜色选择（微信读书风格）
        val colorRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        val selectedColor = mark.bookmark.color
        for (c in BookmarkColors.palette) {
            val isSelected = c == selectedColor
            val dot = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(if (isSelected) 2.dpToPx().toInt() else 0, ctx.getCompatColor(R.color.primaryText))
                }
                isClickable = true
                contentDescription = "#$c"
                setOnClickListener {
                    popup?.dismiss()
                    callBack.changeBookmarkColor(mark.bookmark, c)
                }
            }
            colorRow.addView(dot, LinearLayout.LayoutParams(circleSize, circleSize).apply {
                marginEnd = dp(10)
            })
        }
        card.addView(colorRow)
        fun actionButton(str: String, onClick: () -> Unit): TextView {
            return TextView(ctx).apply {
                text = str
                setTextColor(ctx.getCompatColor(R.color.primaryText))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setBackgroundResource(R.drawable.bg_popup_action_item)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                isClickable = true
                setOnClickListener {
                    popup?.dismiss()
                    onClick()
                }
            }
        }
        buttonRow.addView(actionButton(ctx.getString(R.string.edit)) {
            callBack.onBookmarkEdit(mark.bookmark)
        })
        buttonRow.addView(actionButton(ctx.getString(R.string.delete)) {
            callBack.deleteBookmarkPreview(mark.bookmark)
        })
        card.addView(buttonRow)

        val maxW = (width * 0.72f).toInt().coerceAtLeast(dp(120))
        card.measure(
            View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val pw = card.measuredWidth
        val ph = card.measuredHeight
        card.layout(0, 0, pw, ph)
        popup = PopupWindow(card, pw, ph, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val gap = dp(10)
        val px = (x - pw / 2f).toInt()
            .coerceIn(dp(6), (width - pw - dp(6)).coerceAtLeast(dp(6)))
        val py = if (y - ph - gap > 0) (y - ph - gap).toInt() else (y + gap).toInt()
        popup?.showAtLocation(this, Gravity.TOP or Gravity.START, px, py)
    }

    //endregion

    private fun pageHorizontalOffset(relativePos: Int): Float {
        return if (!callBack.isScroll && relativePos == 1 && ChapterProvider.doublePage) {
            width / 2f
        } else {
            0f
        }
    }

    private fun isInRelativePage(x: Float, relativePos: Int): Boolean {
        if (callBack.isScroll || !ChapterProvider.doublePage) return true
        val halfWidth = width / 2f
        return if (relativePos == 0) x < halfWidth else x >= halfWidth && pairedTextPage != null
    }

    private fun relativeOffset(relativePos: Int): Float {
        if (!callBack.isScroll && ChapterProvider.doublePage) {
            return pageOffset.toFloat()
        }
        return when (relativePos) {
            0 -> pageOffset.toFloat()
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + pageFactory.nextPage.height
        }
    }

    fun relativePage(relativePos: Int): TextPage {
        if (!callBack.isScroll && relativePos == 1 && ChapterProvider.doublePage) {
            return pairedTextPage ?: TextPage().format()
        }
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory.nextPage
            else -> pageFactory.nextPlusPage
        }
    }

    fun setAutoPager(autoPager: AutoPager?) {
        this.autoPager = autoPager
    }

    fun setIsScroll(value: Boolean) {
        val changed = isScroll != value
        isScroll = value
        if (changed) {
            backgroundScrollOffset = 0
            invalidateBackgroundHost()
        }
    }

    override fun canScrollVertically(direction: Int): Boolean {
        if (!callBack.isScroll) return false
        return if (direction < 0) pageFactory.hasPrev() else pageFactory.hasNext()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longScreenshot = true
                scrollY = 0
            }

            MotionEvent.ACTION_UP -> {
                longScreenshot = false
                scrollY = 0
            }
        }
        return callBack.onLongScreenshotTouchEvent(event)
    }

    companion object {
        private val renderThread by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "TextPageRender")
            }
        }
        private val footnoteThread by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "EpubFootnote")
            }
        }
        private val cursorWidth = 24.dpToPx()
        private const val EPUB_MEDIA_LINK_PREFIX = "legado-epub-media:"
    }

    interface CallBack {
        val headerHeight: Int
        val imgBgPaddingStart: Int
        val pageFactory: TextPageFactory
        val pageDelegate: PageDelegate?
        val isScroll: Boolean
        var isSelectingSearchResult: Boolean
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onImageLongPress(
            x: Float,
            y: Float,
            src: String,
            paragraphNum: Int,
            imageIndexInParagraph: Int
        )
        fun onCancelSelect()
        fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean
        fun oldClickImg(src: String): Boolean
        fun clickImg(click: String, src: String)
        fun onBookmarkEdit(bookmark: Bookmark)
        fun deleteBookmarkPreview(bookmark: Bookmark)
        fun changeBookmarkColor(bookmark: Bookmark, color: Int)
    }
}
