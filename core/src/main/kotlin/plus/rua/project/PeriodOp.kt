package plus.rua.project

import kotlinx.datetime.LocalDate

/**
 * 用户对经期文档的一次语义修改。
 *
 * 所有操作都幂等：同步冲突时会在服务器最新文档上重放，目标已被别处改动时保持原样。
 */
sealed interface PeriodOp {
    /** 经期来了：新增进行中的区间；已有进行中区间或 [date] 不晚于最近一段经期时不变。 */
    data class StartPeriod(
        val date: LocalDate,
    ) : PeriodOp

    /** 经期结束：把进行中区间的结束日设为 [date]。 */
    data class EndPeriod(
        val date: LocalDate,
    ) : PeriodOp

    /**
     * 标记或取消某天为经期。进行中的区间先按 [today] 展开，设置后合并或拆分，
     * 结束于 [today] 的那段恢复为进行中；在没有进行中区间时标记今天，也视为经期开始。
     */
    data class SetPeriodDay(
        val date: LocalDate,
        val isPeriod: Boolean,
        val today: LocalDate,
    ) : PeriodOp

    /** 修改开始日为 [oldStart] 的区间；[end] 为 null 时只允许是最近一段。 */
    data class UpdateRange(
        val oldStart: LocalDate,
        val start: LocalDate,
        val end: LocalDate?,
    ) : PeriodOp

    data class DeleteRange(
        val start: LocalDate,
    ) : PeriodOp

    /** 心情和备注都为空时删除这天的记录。 */
    data class SetNote(
        val date: LocalDate,
        val mood: PeriodMood?,
        val text: String,
    ) : PeriodOp

    data class UpdateSettings(
        val settings: PeriodSettings,
    ) : PeriodOp
}

/** 把操作应用到 [document]，结果已规范化但未校验，需配合 [PeriodDocument.violation] 使用。 */
fun PeriodOp.applyTo(document: PeriodDocument): PeriodDocument {
    val doc = document.normalized()
    val changed =
        when (this) {
            is PeriodOp.StartPeriod -> {
                val latest = doc.ranges.lastOrNull()
                val latestEnd = latest?.end
                when {
                    latest == null -> doc.copy(ranges = listOf(PeriodRange(date, null)))
                    latestEnd == null || date <= latestEnd -> doc
                    else -> doc.copy(ranges = doc.ranges + PeriodRange(date, null))
                }
            }

            is PeriodOp.EndPeriod -> {
                val ongoing = doc.ongoing
                if (ongoing == null || date < ongoing.start) {
                    doc
                } else {
                    doc.copy(ranges = doc.ranges.dropLast(1) + ongoing.copy(end = date))
                }
            }

            is PeriodOp.SetPeriodDay -> {
                setPeriodDay(doc)
            }

            is PeriodOp.UpdateRange -> {
                val others = doc.ranges.filter { it.start != oldStart }
                when {
                    others.size == doc.ranges.size -> doc
                    end == null && others.any { it.start >= start } -> doc
                    else -> doc.copy(ranges = others + PeriodRange(start, end))
                }
            }

            is PeriodOp.DeleteRange -> {
                doc.copy(ranges = doc.ranges.filter { it.start != start })
            }

            is PeriodOp.SetNote -> {
                doc.copy(notes = doc.notes.filter { it.date != date } + PeriodNote(date, mood, text))
            }

            is PeriodOp.UpdateSettings -> {
                doc.copy(settings = settings)
            }
        }
    return changed.normalized()
}

/** 应用后仍满足全部不变量才返回新文档，否则返回 null。 */
fun PeriodOp.applyValidated(document: PeriodDocument): PeriodDocument? = applyTo(document).takeIf { it.violation() == null }

/** 依次重放 [ops]；已不适用（应用后违反不变量）的操作跳过。 */
fun PeriodDocument.replay(ops: List<PeriodOp>): PeriodDocument = ops.fold(this) { doc, op -> op.applyValidated(doc) ?: doc }

private fun PeriodOp.SetPeriodDay.setPeriodDay(doc: PeriodDocument): PeriodDocument {
    if (date > today) return doc
    val ongoingEnd = doc.ongoing?.endOn(today)
    val closed = doc.ranges.map { PeriodRange(it.start, it.endOn(today)) }
    val edited =
        if (isPeriod) {
            closed + PeriodRange(date, date)
        } else {
            closed.flatMap { range ->
                val end = range.end ?: return@flatMap listOf(range)
                if (date < range.start || date > end) {
                    listOf(range)
                } else {
                    listOfNotNull(
                        PeriodRange(range.start, PeriodDocument.dayBefore(date)).takeIf { date > range.start },
                        PeriodRange(PeriodDocument.dayAfter(date), end).takeIf { date < end },
                    )
                }
            }
        }
    val reopenAt = ongoingEnd ?: today.takeIf { isPeriod && date == today }
    val merged = doc.copy(ranges = edited).normalized().ranges
    return doc.copy(ranges = merged.map { if (reopenAt != null && it.end == reopenAt) it.copy(end = null) else it })
}
