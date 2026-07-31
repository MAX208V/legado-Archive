package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 小说→视频 任务实体。
 *
 * 一个任务对应一本书的一段章节范围（单章或多章）。任务内部按章节切分子任务，
 * 每章生成一组 [NovelVideoSegment]，最终合并为一段视频（或多段，每章一段）。
 *
 * 状态流转见 [NovelVideoJobStatus]。
 */
@Parcelize
@Entity(
    tableName = "novel_video_jobs",
    indices = [
        Index("bookUrl"),
        Index("status"),
        Index("createdAt")
    ]
)
data class NovelVideoJob(
    @PrimaryKey
    val id: String,
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    @ColumnInfo(defaultValue = "-1")
    val chapterStartIndex: Int = -1,
    @ColumnInfo(defaultValue = "-1")
    val chapterEndIndex: Int = -1,
    @ColumnInfo(defaultValue = "[]")
    val chapterTitlesJson: String = "[]",
    @ColumnInfo(defaultValue = NovelVideoJobStatus.DRAFTING)
    val status: String = NovelVideoJobStatus.DRAFTING,
    @ColumnInfo(defaultValue = "{}")
    val paramsJson: String = "{}",
    @ColumnInfo
    val screenplayJson: String? = null,
    @ColumnInfo
    val draftJson: String? = null,
    @ColumnInfo
    val outputPath: String? = null,
    @ColumnInfo
    val coverPath: String? = null,
    @ColumnInfo
    val totalDurationMs: Long? = null,
    @ColumnInfo
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val attachToBookChapter: Boolean = true,
    /** 认领该 job 的 worker 标识（单进程固定 "default"）。null=未被认领。P0 引入。 */
    @ColumnInfo
    val workerId: String? = null,
    /** worker 最近心跳时间（epoch ms）。null=未被认领。P0 引入。 */
    @ColumnInfo
    val workerHeartbeatAt: Long? = null,
    /** Stage 6 提交后从 backend 返回的 provider 端任务 ID（job 级，跨 stage resume 用）。P0 引入。 */
    @ColumnInfo
    val providerJobId: String? = null,
    /** 入队时派生的 video provider ID（NON_RESUMABLE 分流 + resume 锁定）。P0 引入。 */
    @ColumnInfo
    val providerId: String? = null,
    /** 尝试次数（默认 0）。P0 引入。 */
    @ColumnInfo(defaultValue = "0")
    val attempts: Int = 0,
    /** 下次可调度时间（epoch ms，默认 0 立即可调度，回队延迟用）。P0 引入。 */
    @ColumnInfo(defaultValue = "0")
    val availableAt: Long = 0
) : Parcelable {

    val isRunning: Boolean
        get() = status in NovelVideoJobStatus.RUNNING_STATES

    val isFinished: Boolean
        get() = status in NovelVideoJobStatus.FINISHED_STATES

    val isFailed: Boolean
        get() = status == NovelVideoJobStatus.FAILED || status == NovelVideoJobStatus.PARTIAL_FAILED
}

/** 任务状态枚举（以字符串常量形式存库，避免 Room 枚举转换器开销）。 */
object NovelVideoJobStatus {
    const val DRAFTING = "drafting"
    const val SCREENPLAY_PENDING_REVIEW = "screenplay_pending_review"
    const val SCREENPLAY_CONFIRMED = "screenplay_confirmed"
    const val GENERATING = "generating"
    const val MERGING = "merging"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val PARTIAL_FAILED = "partial_failed"
    const val CANCELLED = "cancelled"
    const val PAUSED = "paused"

    /** 处于运行中（任务中心「进行中」Tab）。 */
    val RUNNING_STATES = setOf(
        DRAFTING,
        SCREENPLAY_PENDING_REVIEW,
        SCREENPLAY_CONFIRMED,
        GENERATING,
        MERGING,
        PAUSED
    )

    /** 已终结（任务中心「已完成」或「失败」Tab）。 */
    val FINISHED_STATES = setOf(
        COMPLETED,
        FAILED,
        PARTIAL_FAILED,
        CANCELLED
    )
}
