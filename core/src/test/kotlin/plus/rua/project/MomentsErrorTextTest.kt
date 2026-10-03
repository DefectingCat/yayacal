package plus.rua.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 错误文案只呈现可理解的恢复说明，不泄露服务器地址或原始响应。 */
class MomentsErrorTextTest {
    @Test fun unknownFailure_doesNotExposeResponseOrLocalPath() {
        val message = momentsErrorDescription("Unexpected response {token: private} at /private/cache/upload")
        assertEquals("暂时未能完成，请稍后再试", message)
        assertFalse(message.contains("private"))
    }

    @Test fun commonFailures_explainSpecificRecovery() {
        assertEquals("连接等待有些久，请稍后再试", momentsErrorDescription("网络超时"))
        assertEquals("请检查网络连接，或稍后再试", momentsErrorDescription("离线"))
        assertEquals("图片过大，请选择不超过 50 MiB 的图片", momentsErrorDescription("图片不能超过 50 MiB"))
        assertEquals("图片过大，请选择不超过 50 MiB 的图片", momentsErrorDescription("请求失败（413）"))
        assertEquals("存储空间不足，请清理一些空间后再试", momentsErrorDescription("草稿保存失败，请检查存储空间"))
        assertEquals("内容已被删除或当前账号无法查看", momentsErrorDescription("请求失败（404）"))
    }
}
