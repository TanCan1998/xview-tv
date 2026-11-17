package io.github.tancan1998.xviewtv.core.network

import org.junit.Assert.*
import org.junit.Test

class WebTranslationParserTest {
    @Test fun completeJson() {
        assertEquals("完整译文", WebTranslationParser.text("""{"result":{"text":"完整译文"}}"""))
    }
    @Test fun newlineDeltasAreNotTruncatedAtFirstCharacter() {
        val raw = listOf("你", "好", "世", "界").joinToString("\n") { "{\"result\":{\"text\":\"$it\"}}" }
        assertEquals("你好世界", WebTranslationParser.text(raw))
    }
    @Test fun sseCumulativeSnapshotsReplacePreviousPrefix() {
        val raw = "data: {\"result\":{\"text\":\"你\"}}\n\ndata: {\"result\":{\"text\":\"你好\"}}\n\ndata: [DONE]"
        assertEquals("你好", WebTranslationParser.text(raw))
    }
    @Test fun emptyResponseDoesNotReplaceOriginal() {
        assertNull(WebTranslationParser.text("{}"))
    }
}
