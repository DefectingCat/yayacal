package plus.rua.project

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 五线谱练习模型（[StaffNote] / [StaffGeometry] / [StaffQuiz]）单元测试。
 *
 * 只覆盖纯 Kotlin 逻辑：音名/唱名/八度映射、五线谱纵坐标与加线计算、
 * 出题器的不变量（答案唯一、选项不重复、范围内、防连题、种子可复现）。
 */
class StaffNotesTest {
    // region StaffNote 映射

    @Test
    fun staffNote_step0_isMiddleC() {
        val note = StaffNote(0)
        assertEquals("C", note.pitchName)
        assertEquals(Solfege.DO, note.solfege)
        assertEquals(4, note.octave)
    }

    @Test
    fun staffNote_oneOctave_mapsAllSolfege() {
        val expected =
            listOf(
                "C" to Solfege.DO,
                "D" to Solfege.RE,
                "E" to Solfege.MI,
                "F" to Solfege.FA,
                "G" to Solfege.SOL,
                "A" to Solfege.LA,
                "B" to Solfege.SI,
            )
        expected.forEachIndexed { step, (pitch, solfege) ->
            assertEquals(pitch, StaffNote(step).pitchName, "step=$step 音名")
            assertEquals(solfege, StaffNote(step).solfege, "step=$step 唱名")
        }
    }

    @Test
    fun solfege_numbers_runOneToSeven() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7),
            Solfege.entries.map { it.number },
        )
    }

    @Test
    fun staffNote_octave_rollsAtC() {
        assertEquals(4, StaffNote(6).octave) // B4
        assertEquals(5, StaffNote(7).octave) // C5
        assertEquals(6, StaffNote(14).octave) // C6
    }

    // endregion

    // region StaffGeometry 几何

    @Test
    fun halfUnits_bottomLine_isZero() {
        assertEquals(0, StaffGeometry.halfUnitsFromBottomLine(StaffGeometry.BOTTOM_LINE_STEP))
        assertEquals(8, StaffGeometry.halfUnitsFromBottomLine(StaffGeometry.TOP_LINE_STEP))
    }

    @Test
    fun ledgerOffsets_notesOnStaff_empty() {
        // 下一线 E4（step 2）到上五线 F5（step 10）之间的音都不需要加线，
        // 含紧邻谱面的两个间：D4（step 1）、G5（step 11）
        (1..11).forEach { step ->
            assertEquals(emptyList(), StaffGeometry.ledgerOffsets(step), "step=$step")
        }
    }

    @Test
    fun ledgerOffsets_middleC_oneLineBelow() {
        assertEquals(listOf(-2), StaffGeometry.ledgerOffsets(0))
    }

    @Test
    fun ledgerOffsets_aboveStaff_includesIntermediateLines() {
        // A5（上加一线）
        assertEquals(listOf(10), StaffGeometry.ledgerOffsets(12))
        // B5（上加二间）：音符在间上，仍要画出 A5 的加线
        assertEquals(listOf(10), StaffGeometry.ledgerOffsets(13))
        // C6（上加二线）
        assertEquals(listOf(10, 12), StaffGeometry.ledgerOffsets(14))
    }

    @Test
    fun clef_bass_geometry_andLedgerOffsets() {
        // 低音谱表下一线为 G2 (step -10)
        assertEquals(0, Clef.BASS.halfUnitsFromBottomLine(-10))
        // 第四线为 F3 (step -4)
        assertEquals(6, Clef.BASS.halfUnitsFromBottomLine(-4))
        // 第五线为 A3 (step -2)
        assertEquals(8, Clef.BASS.halfUnitsFromBottomLine(-2))
        // 中央 C4 (step 0) 为上加一线
        assertEquals(10, Clef.BASS.halfUnitsFromBottomLine(0))
        assertEquals(listOf(10), Clef.BASS.ledgerOffsets(0))
        // 低音 C2 (step -14) 为下加二线
        assertEquals(-4, Clef.BASS.halfUnitsFromBottomLine(-14))
        assertEquals(listOf(-2, -4), Clef.BASS.ledgerOffsets(-14))
    }

    // endregion

    // region StaffQuiz 出题器

    @Test
    fun generator_anyQuestion_containsAnswerExactlyOnce() {
        val generator = StaffQuiz.Generator(random = Random(42))
        repeat(50) {
            val question = generator.next(StaffQuiz.Direction.NOTE_TO_SOLFEGE)
            assertEquals(4, question.options.size)
            assertEquals(1, question.options.count { it == question.answer })
            assertEquals(question.options.size, question.options.distinct().size)
        }
    }

    @Test
    fun generator_anyDirection_optionsHaveDistinctSolfege() {
        // 回归：B4(step 6) 与 B5(step 13) 唱名同为 si，
        // 曾导致「看谱认唱名」选项出现两个 si、「听名找位置」出现两个正确答案
        val generator = StaffQuiz.Generator(random = Random(2026))
        repeat(200) {
            StaffQuiz.Direction.entries.forEach { direction ->
                val question = generator.next(direction)
                assertEquals(
                    question.options.size,
                    question.options.map { it.solfege }.distinct().size,
                    "选项唱名重复：${question.options}",
                )
            }
        }
    }

    @Test
    fun generator_excludeStep_neverRepeatsPreviousAnswer() {
        val generator = StaffQuiz.Generator(random = Random(7))
        var previous: Int? = null
        repeat(100) {
            val question =
                generator.next(StaffQuiz.Direction.SOLFEGE_TO_NOTE, excludeStep = previous)
            assertTrue(question.answer.step != previous, "连续两题答案相同：${question.answer.step}")
            previous = question.answer.step
        }
    }

    @Test
    fun generator_customRange_optionsStayInside() {
        val generator = StaffQuiz.Generator(range = 3..9, random = Random(1))
        repeat(50) {
            val question = generator.next(StaffQuiz.Direction.NOTE_TO_SOLFEGE)
            question.options.forEach { option ->
                assertTrue(option.step in 3..9, "选项越界：${option.step}")
            }
        }
    }

    @Test
    fun generator_rangeSmallerThanOptionCount_throws() {
        assertFailsWith<IllegalArgumentException> {
            StaffQuiz.Generator(range = 0..2, optionCount = 4)
        }
    }

    @Test
    fun generator_sameSeed_reproducesQuestions() {
        val first = StaffQuiz.Generator(random = Random(99))
        val second = StaffQuiz.Generator(random = Random(99))
        repeat(10) {
            assertEquals(
                first.next(StaffQuiz.Direction.NOTE_TO_SOLFEGE),
                second.next(StaffQuiz.Direction.NOTE_TO_SOLFEGE),
            )
        }
    }

    @Test
    fun generator_bassClef_optionsAndAnswersWithinBassRange() {
        val generator =
            StaffQuiz.Generator(
                clefMode = StaffQuiz.QuizClefMode.BASS,
                difficulty = StaffQuiz.QuizDifficulty.BASIC,
                random = Random(123),
            )
        repeat(50) {
            val question = generator.next(StaffQuiz.Direction.NOTE_TO_SOLFEGE)
            assertEquals(Clef.BASS, question.clef)
            assertTrue(question.answer.step in -10..-2, "低音基础答案越界：${question.answer.step}")
            question.options.forEach { option ->
                assertTrue(option.step in -10..-2, "低音基础选项越界：${option.step}")
            }
        }
    }

    @Test
    fun generator_mixedMode_generatesBothTrebleAndBass() {
        val generator =
            StaffQuiz.Generator(
                clefMode = StaffQuiz.QuizClefMode.MIXED,
                difficulty = StaffQuiz.QuizDifficulty.FULL,
                random = Random(456),
            )
        val clefs = mutableSetOf<Clef>()
        repeat(100) {
            val question = generator.next(StaffQuiz.Direction.NOTE_TO_SOLFEGE)
            clefs += question.clef
            if (question.clef == Clef.TREBLE) {
                assertTrue(question.answer.step in 0..14)
            } else {
                assertTrue(question.answer.step in -14..0)
            }
        }
        assertEquals(setOf(Clef.TREBLE, Clef.BASS), clefs, "双谱表混合应同时包含高音谱和低音谱")
    }

    // endregion
}
