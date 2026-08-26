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
     * 音符相对下一线的半线距偏移。
     *
     * 第一线=0、第五线=8；下加一线（C4）=-2；上加一线（A5）=10。
     */
    fun halfUnitsFromBottomLine(step: Int): Int = step - BOTTOM_LINE_STEP

    /**
     * 该音需要画的加线（相对下一线的半线距偏移列表，由近及远）。
     *
     * 音符在五线谱内（含紧邻的间上）时为空；音符落在谱外间上时，
     * 仍需画出它下方/上方到谱面之间的所有加线（如 B5 要带 A5 的加线）。
     */
    fun ledgerOffsets(step: Int): List<Int> {
        val offset = halfUnitsFromBottomLine(step)
        // 谱面最低间（D4，-1）到最高间（G5，9）之间不需要加线
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
     * 一道练习题。
     *
     * 不变量：[answer] 在 [options] 中恰好出现一次；options 的唱名互不重复——
     * 同一唱名的不同八度（如 B4/B5）在「看谱认唱名」里是相同选项、
     * 在「听名找位置」里是多个正确答案，两种方向都是歧义题，必须排除。
     */
    data class Question(
        val direction: Direction,
        val answer: StaffNote,
        val options: List<StaffNote>,
    )

    /** 默认练习范围：中央 C（C4）到上加二线（C6），两个八度共 15 个自然音。 */
    val DEFAULT_RANGE: IntRange = 0..14

    /**
     * 题目生成器。
     *
     * @param range 出题用的 step 闭区间
     * @param optionCount 每题选项数（含正确答案）
     * @param random 随机源，测试可注入固定种子复现题目
     */
    class Generator(
        private val range: IntRange = DEFAULT_RANGE,
        private val optionCount: Int = 4,
        private val random: Random = Random.Default,
    ) {
        init {
            require(optionCount >= 2) { "选项至少 2 个" }
            require(distinctDegrees(range).size >= optionCount) {
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
            val pool = range.toList()
            val candidates = if (excludeStep != null) pool - excludeStep else pool
            val answer = StaffNote(candidates.random(random))
            val answerDegree = answer.step.mod(DEGREE_COUNT)
            val distractors =
                (distinctDegrees(range) - answerDegree)
                    .shuffled(random)
                    .take(optionCount - 1)
                    .map { degree -> pool.filter { it.mod(DEGREE_COUNT) == degree }.random(random) }
            val options =
                (distractors + answer.step)
                    .shuffled(random)
                    .map(::StaffNote)
            return Question(direction, answer, options)
        }
    }

    /** 唱名类总数（C 大调自然音阶 7 个）。 */
    private const val DEGREE_COUNT = 7

    /** 范围内实际出现的唱名类（step mod 7）。 */
    private fun distinctDegrees(range: IntRange): List<Int> = range.map { it.mod(DEGREE_COUNT) }.distinct()
}
