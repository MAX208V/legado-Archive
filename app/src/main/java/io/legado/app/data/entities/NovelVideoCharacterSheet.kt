package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 角色三视图（单张组合图，含正面/侧面/背面）。隶属某个 [NovelVideoJob]。
 *
 * 用于跨场景生图时保持人物一致性（director_ai 的 character_sheet 概念）。
 */
@Parcelize
@Entity(
    tableName = "novel_video_character_sheets",
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
        Index(value = ["jobId", "characterId"], unique = true)
    ]
)
data class NovelVideoCharacterSheet(
    @PrimaryKey
    val id: String,
    @ColumnInfo(defaultValue = "")
    val jobId: String = "",
    @ColumnInfo(defaultValue = "")
    val characterId: String = "",
    @ColumnInfo(defaultValue = "")
    val characterName: String = "",
    @ColumnInfo(defaultValue = "")
    val description: String = "",
    @ColumnInfo(defaultValue = "主角")
    val role: String = "主角",
    @ColumnInfo
    val combinedViewUrl: String? = null,
    @ColumnInfo
    val localPath: String? = null,
    @ColumnInfo(defaultValue = NovelVideoCharacterSheetStatus.PENDING)
    val status: String = NovelVideoCharacterSheetStatus.PENDING,
    @ColumnInfo
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    val isCompleted: Boolean
        get() = status == NovelVideoCharacterSheetStatus.COMPLETED && !combinedViewUrl.isNullOrEmpty()
}

object NovelVideoCharacterSheetStatus {
    const val PENDING = "pending"
    const val GENERATING = "generating"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
}
