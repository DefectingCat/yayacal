package plus.rua.project.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AboutEasterEggTest {

    @Test
    fun `first three clicks show no toast`() {
        assertNull(getToastMessage(1))
        assertNull(getToastMessage(2))
        assertNull(getToastMessage(3))
    }

    @Test
    fun `fourth click shows remaining three`() {
        assertEquals("再点击 3 下进入小狗乐园", getToastMessage(4))
    }

    @Test
    fun `fifth click shows remaining two`() {
        assertEquals("再点击 2 下进入小狗乐园", getToastMessage(5))
    }

    @Test
    fun `sixth click shows remaining one`() {
        assertEquals("再点击 1 下进入小狗乐园", getToastMessage(6))
    }

    @Test
    fun `seventh click triggers navigation and shows no toast`() {
        assertNull(getToastMessage(7))
    }
}
