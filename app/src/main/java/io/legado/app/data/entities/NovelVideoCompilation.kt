package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

/**
 * 整部视频（跨章节拼接产物）实体。子项目 E 引入。
 *
 * 一个 [NovelVideoCompilation] 由 ≥2 个同书已完成 [NovelVideoJob] 的合并产物
 * （`novel_video/<jobId>/merged.mp4`）经 [io.legado.app.help.ai.VideoMuxer.merge]
 * 无损拼接而成，落盘到 `novel_video/compilations/<id>/full.mp4`。
 *
 * 设计要点：
 * - **不建 FK 到 `novel_video_jobs`**：源 job 可被独立删除，已产出的整部视频文件自包含
 *   不受影响（[sourceJobIdsJson] 仅作来源记录，删除源 job 不会级联删除 compilation）。
 * - **[sourceJobIdsJson] 按章节顺序存储**：编译时按 `chapterStartIndex` 升序排列后存档。
 *
 * 详见 `docs/superpowers/specs/2026-07-08-novel-video-cross-chapter-compilation-design.md`。
 */
@Parcelize
@Entity(
    tableName = "novel_video_compilations",
    indices = [
        Index("bookUrl"),
        Index("createdAt")
    ]
)
data class NovelVideoCompilation(
    @PrimaryKey
    val id: String,
    @ColumnInfo(defaultValue = "")
    val title: String = "",
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    /** 源 job ID 列表（JSON 数组），按 chapterStartIndex 升序。 */
    @ColumnInfo(defaultValue = "[]")
    val sourceJobIdsJson: String = "[]",
    /** 产出文件绝对路径 `novel_video/compilations/<id>/full.mp4`。null=编译中/失败。 */
    @ColumnInfo
    val outputPath: String? = null,
    @ColumnInfo
    val totalDurationMs: Long? = null,
    /** = sourceJobIds.size。 */
    @ColumnInfo(defaultValue = "0")
    val segmentCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable
