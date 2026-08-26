package plus.rua.project

import kotlin.random.Random

/**
 * 唱名（固定 do 唱名法，C 大调自然音阶）。
 *
 * @property label 展示用的小写唱名文本（do / re / mi / fa / sol / la / si）
 * @property number 对应的简谱数字（do=1 … si=7）
 */
enum class Solfege(val label: String, val number: Int) {
    DO("do", 1),
    RE("re", 2),
    MI("mi", 3),
    FA("fa", 4),
    SOL("sol", 5),
    LA("la", 6),
    SI("si", 7),
}

/**
 * 高音谱表上的一个自然音（白键），用距中央 C 的自然音级数 [step] 定位。
 *
 * step 0 = C4（中央 C，下加一线），每 +1 上行一个自然音：
 * 0=C4, 1=D4, 2=E4（第一线）, …, 7=C5, …, 14=C6（上加二线）。
 * 只覆盖 C 大调自然音，不含升降号。
 */
@JvmInline
value class StaffNote(val step: Int) {
    /** 音名（C D E F G A B）。 */
    val pitchName: String
        get() = PITCH_NAMES[step.mod(PITCH_NAMES.size)]

    /** 唱名（do ~ si）。 */
    val solfege: Solfege
        get() = Solfege.entries[step.mod(PITCH_NAMES.size)]

    /** 八度（科学音高记法，中央 C = C4）。 */
    val octave: Int
        get() = 4 + step.floorDiv(PITCH_NAMES.size)

    private companion object {
        val PITCH_NAMES = listOf("C", "D", "E", "F", "G", "A", "B")
    }
}

/**
 * 五线谱谱表类型。
 *
 * @property label 展示名称
 * @property bottomLineStep 下一线（第一线）对应的 step
 * @property topLineStep 上五线（第五线）对应的 step
 */
enum class Clef(
    val label: String,
    val bottomLineStep: Int,
    val topLineStep: Int,
) {
    /** 高音谱表（G 谱号）：下一线 E4 (step 2)，上五线 F5 (step 10)。 */
    TREBLE("高音谱表", 2, 10),

    /** 低音谱表（F 谱号）：下一线 G2 (step -10)，上五线 A3 (step -2)。 */
    BASS("低音谱表", -10, -2),
    ;

    /** 音符相对该谱表下一线的半线距偏移。 */
    fun halfUnitsFromBottomLine(step: Int): Int = step - bottomLineStep

    /** 该音在当前谱表需要画的加线（相对下一线的半线距偏移列表，由近及远）。 */
    fun ledgerOffsets(step: Int): List<Int> {
        val offset = halfUnitsFromBottomLine(step)
        // 谱面最低间（-1）到最高间（9）之间不需要加线
        if (offset in -1..9) return emptyList()
        val first = if (offset < 0) -2 else 10
        val direction = if (offset < 0) -2 else 2
        val result = mutableListOf<Int>()
        var current = first
        while (if (direction < 0) current >= offset else current <= offset) {
            result += current
            current += direction
        }
        return result
    }
}

/**
 * 高音谱表五线谱的几何换算：自然音级 ↔ 五线谱纵坐标。
 *
 * 纵坐标以「半线距」为单位（相邻两个自然音相差半个线距），
 * 0 点在下一线（E4）。偶数偏移落在线上，奇数偏移落在间上。
 */
object StaffGeometry {
    /** 下一线（E4）对应的 step。 */
    const val BOTTOM_LINE_STEP = 2

    /** 上五线（F5）对应的 step。 */
    const val TOP_LINE_STEP = 10

    /**
     * 音符相对谱表下一线的半线距偏移。
     *
     * 高音谱表：第一线=0、第五线=8；下加一线（C4）=-2；上加一线（A5）=10。
     */
    fun halfUnitsFromBottomLine(step: Int, clef: Clef = Clef.TREBLE): Int = clef.halfUnitsFromBottomLine(step)

    /**
     * 该音需要画的加线（相对下一线的半线距偏移列表，由近及远）。
     *
     * 音符在五线谱内（含紧邻的间上）时为空；音符落在谱外间上时，
     * 仍需画出它下方/上方到谱面之间的所有加线（如 B5 要带 A5 的加线）。
     */
    fun ledgerOffsets(step: Int, clef: Clef = Clef.TREBLE): List<Int> = clef.ledgerOffsets(step)
}

/**
 * 五线谱练习出题器：双向识谱练习的题目模型与生成逻辑。
 */
object StaffQuiz {
    /**
     * 练习方向。
     *
     * [NOTE_TO_SOLFEGE] 看谱认唱名：上方显示音符，下方选唱名；
     * [SOLFEGE_TO_NOTE] 听名找位置：上方显示唱名，下方选五线谱上的音符。
     */
    enum class Direction {
        NOTE_TO_SOLFEGE,
        SOLFEGE_TO_NOTE,
    }

    /**
     * 练习的谱表模式。
     */
    enum class QuizClefMode(val label: String, val icon: String) {
        /** 仅高音谱表。 */
        TREBLE("高音谱表", "🎼"),

        /** 仅低音谱表。 */
        BASS("低音谱表", "𝄢"),

        /** 高低音双谱表混合（随机出高音谱或低音谱题目）。 */
        MIXED("双谱表混合", "🎹"),
    }

    /**
     * 练习的音域难度。
     */
    enum class QuizDifficulty(val label: String) {
        /** 基础音域：五线谱内自然音（不含繁复加线）。 */
        BASIC("基础五线内"),

        /** 完整音域：包含上下加线全音域。 */
        FULL("全音域加线"),
    }

    /**
     * 一道练习题。
     *
     * 不变量：[answer] 在 [options] 中恰好出现一次；options 的唱名互不重复——
     * 同一唱名的不同八度（如 B4/B5）在「看谱认唱名」里是相同选项、
     * 在「听名找位置」里是多个正确答案，两种方向都是歧义题，必须排除。
     *
     * @property direction 练习方向
     * @property answer 正确答案音符
     * @property options 选项列表（含答案）
     * @property clef 该题所在的谱表类型
     */
    data class Question(
        val direction: Direction,
        val answer: StaffNote,
        val options: List<StaffNote>,
        val clef: Clef = Clef.TREBLE,
    )

    /** 默认高音谱练习范围：中央 C（C4）到上加二线（C6），两个八度共 15 个自然音。 */
    val DEFAULT_RANGE: IntRange = 0..14

    /**
     * 获取指定谱表与难度对应的音符 step 范围。
     */
    fun rangeFor(clef: Clef, difficulty: QuizDifficulty): IntRange = when (clef) {
        Clef.TREBLE ->
            when (difficulty) {
                QuizDifficulty.BASIC -> 2..10

                // E4 ~ F5
                QuizDifficulty.FULL -> 0..14 // C4 ~ C6
            }

        Clef.BASS ->
            when (difficulty) {
                QuizDifficulty.BASIC -> -10..-2

                // G2 ~ A3
                QuizDifficulty.FULL -> -14..0 // C2 ~ C4
            }
    }

    /**
     * 题目生成器。
     *
     * @param clefMode 练习的谱表模式
     * @param difficulty 练习音域难度
     * @param customRange 自定义出题 step 范围（传 null 时按 clefMode 和 difficulty 自动计算）
     * @param optionCount 每题选项数（含正确答案）
     * @param random 随机源，测试可注入固定种子复现题目
     */
    class Generator(
        val clefMode: QuizClefMode = QuizClefMode.TREBLE,
        val difficulty: QuizDifficulty = QuizDifficulty.FULL,
        private val customRange: IntRange? = null,
        private val optionCount: Int = 4,
        private val random: Random = Random.Default,
    ) {
        /** 辅助构造器：支持直接传入自定义 range（兼顾既有单测与固定区间出题）。 */
        constructor(
            range: IntRange,
            optionCount: Int = 4,
            random: Random = Random.Default,
        ) : this(
            clefMode = QuizClefMode.TREBLE,
            difficulty = QuizDifficulty.FULL,
            customRange = range,
            optionCount = optionCount,
            random = random,
        )

        init {
            require(optionCount >= 2) { "选项至少 2 个" }
            val sampleRange = customRange ?: rangeFor(Clef.TREBLE, difficulty)
            require(distinctDegrees(sampleRange).size >= optionCount) {
                "范围内不同唱名数必须不少于选项数"
            }
        }

        /**
         * 生成下一题。干扰项与答案唱名互不相同（允许同唱名类内随机选八度）。
         *
         * @param direction 练习方向
         * @param excludeStep 上一题答案的 step，避免连续两题相同；传 null 不排除
         */
        fun next(direction: Direction, excludeStep: Int? = null): Question {
            val targetClef =
                when (clefMode) {
                    QuizClefMode.TREBLE -> Clef.TREBLE
                    QuizClefMode.BASS -> Clef.BASS
                    QuizClefMode.MIXED -> if (random.nextBoolean()) Clef.TREBLE else Clef.BASS
                }
            val activeRange = customRange ?: rangeFor(targetClef, difficulty)
            val pool = activeRange.toList()
            val candidates = if (excludeStep != null) pool - excludeStep else pool
            val safeCandidates = candidates.ifEmpty { pool }
            val answer = StaffNote(safeCandidates.random(random))
            val answerDegree = answer.step.mod(DEGREE_COUNT)
            val distractors =
                (distinctDegrees(activeRange) - answerDegree)
                    .shuffled(random)
                    .take(optionCount - 1)
                    .map { degree -> pool.filter { it.mod(DEGREE_COUNT) == degree }.random(random) }
            val options =
                (distractors + answer.step)
                    .shuffled(random)
                    .map(::StaffNote)
            return Question(direction, answer, options, targetClef)
        }
    }

    /** 唱名类总数（C 大调自然音阶 7 个）。 */
    private const val DEGREE_COUNT = 7

    /** 范围内实际出现的唱名类（step mod 7）。 */
    private fun distinctDegrees(range: IntRange): List<Int> = range.map { it.mod(DEGREE_COUNT) }.distinct()
}
