package cn.jiayi.familymemory.ui.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaFallbackTextTest {
    @Test
    fun unavailableSystemAsrClearlyFallsBackToManualInput() {
        assertEquals("本机系统识别不可用，请手动填写", asrButtonText(false))
    }
}
