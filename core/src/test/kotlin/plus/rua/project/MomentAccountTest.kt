package plus.rua.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MomentAccountTest {
    @Test
    fun allAccounts_containsXiaoBaiAndXiaoJiMao() {
        assertEquals(2, MomentAccount.ALL_ACCOUNTS.size)
        assertTrue(MomentAccount.ALL_ACCOUNTS.any { it.id == MomentAccount.ID_XIAOBAI && it.name == "小白" })
        assertTrue(MomentAccount.ALL_ACCOUNTS.any { it.id == MomentAccount.ID_XIAOJIMAO && it.name == "小鸡毛" })
    }

    @Test
    fun findById_validId_returnsCorrectAccount() {
        val xiaobai = MomentAccount.findById(MomentAccount.ID_XIAOBAI)
        assertEquals("小白", xiaobai.name)
        assertEquals("avatar_xiaobai.jpg", xiaobai.filename)

        val xiaojimao = MomentAccount.findById(MomentAccount.ID_XIAOJIMAO)
        assertEquals("小鸡毛", xiaojimao.name)
        assertEquals("avatar_xiaojimao.jpg", xiaojimao.filename)
    }

    @Test
    fun findById_nullOrUnknown_defaultsToXiaoBai() {
        assertEquals("小白", MomentAccount.findById(null).name)
        assertEquals("小白", MomentAccount.findById("unknown_id").name)
    }

    @Test
    fun findByName_validName_returnsCorrectAccount() {
        assertEquals(MomentAccount.ID_XIAOBAI, MomentAccount.findByName("小白").id)
        assertEquals(MomentAccount.ID_XIAOJIMAO, MomentAccount.findByName("小鸡毛").id)
    }

    @Test
    fun findByName_nullOrUnknown_defaultsToXiaoBai() {
        assertEquals(MomentAccount.ID_XIAOBAI, MomentAccount.findByName(null).id)
        assertEquals(MomentAccount.ID_XIAOBAI, MomentAccount.findByName("未知").id)
    }
}
