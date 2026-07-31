package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 单个分镜段（一个场景的图+视频）。隶属某个 [NovelVideoJob]，按章节+场景序号定位。
 *
 * 状态机：pending → image_generating → image_completed →
 *         video_generating → video_completed / failed
 */
@Parcelize
@Entity(
    tableName = "novel_video_segments",
    foreignKeys = [
        ForeignKey(
            entity = NovelVideoJob::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("jobId"),
        Index(value = ["jobId", "chapterIndex", "sceneId"], unique = true),
        Index("status")
    ]
)
data class NovelVideoSegment(
    @PrimaryKey
    val id: String,
    @ColumnInfo(defaultValue = "")
    val jobId: String = "",
    @ColumnInfo(defaultValue = "0")
    val chapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val chapterTitle: String = "",
    @ColumnInfo(defaultValue = "1")
    val sceneId: Int = 1,
    @ColumnInfo(defaultValue = "")
    val narration: String = "",
    @ColumnInfo(defaultValue = "")
    val imagePrompt: String = "",
    @ColumnInfo(defaultValue = "")
    val videoPrompt: String = "",
    @ColumnInfo(defaultValue = "")
    val characterDescription: String = "",
    @ColumnInfo
    val imageUrl: String? = null,
    @ColumnInfo
    val videoUrl: String? = null,
    @ColumnInfo
    val localVideoPath: String? = null,
    @ColumnInfo
    val durationMs: Long? = null,
    @ColumnInfo(defaultValue = NovelVideoSegmentStatus.PENDING)
    val status: String = NovelVideoSegmentStatus.PENDING,
    @ColumnInfo(defaultValue = "0")
    val retryCount: Int = 0,
    @ColumnInfo
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    /** Stage 6 视频任务的 provider 端 ID（resume 用）。P0 引入。 */
    @ColumnInfo
    val providerJobId: String? = null,
    /** Stage 6 使用的 video provider（NON_RESUMABLE 分流）。P0 引入。 */
    @ColumnInfo
    val providerId: String? = null
) : Parcelable {

    val isCompleted: Boolean
        get() = status == NovelVideoSegmentStatus.VIDEO_COMPLETED

    val isFailed: Boolean
        get() = status == NovelVideoSegmentStatus.FAILED
}

object NovelVideoSegmentStatus {
    const val PENDING = "pending"
    const val IMAGE_GENERATING = "image_generating"
    const val IMAGE_COMPLETED = "image_completed"
    const val VIDEO_GENERATING = "video_generating"
    const val VIDEO_COMPLETED = "video_completed"
    const val FAILED = "failed"

    /** 可重试/续传的中间态。 */
    val IN_PROGRESS = setOf(
        PENDING,
        IMAGE_GENERATING,
        IMAGE_COMPLETED,
        VIDEO_GENERATING
    )
}
