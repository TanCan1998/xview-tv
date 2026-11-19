package io.github.tancan1998.xviewtv.core.network

import org.junit.Assert.*
import org.junit.Test

class WebSessionTest {
    @Test fun parsesRequiredFieldsAndNeverPrintsValues() {
        val session = WebSession.parse(" Cookie: extra=discard; auth_token = synthetic==; ct0 = csrf=; ")
        assertEquals("synthetic==", session.authToken)
        assertEquals("csrf=", session.csrf)
        assertEquals("WebSession([redacted])", session.toString())
        assertFalse(session.toString().contains("synthetic"))
    }
    @Test fun acceptsBrowserCookieWithJsonQuotedAndDuplicateExtras() {
        val session = WebSession.parse(
            """__cuid=one; __cuid=one; g_state={"i_l":0,"i_e":{"flag":24}}; auth_token=synthetic; ct0=csrf; __cf_bm=one; __cf_bm=two; personalization_id="v1_sample==""""""
        )
        assertEquals("synthetic", session.authToken)
        assertEquals("csrf", session.csrf)
    }

    @Test fun rejectsMalformedMissingAndConflictingValues() {
        for (input in listOf("auth_token=a", "ct0=b", "auth_token=;ct0=b", "auth_token=a;ct0=b;ct0=c",
            "auth_token=a;ct0=b\r\n", "auth_token=a;;ct0=b", "auth_token=a;ct0=b;Path=/", "auth_token=\"a\";ct0=b")) {
            assertThrows(IllegalArgumentException::class.java) { WebSession.parse(input) }
        }
    }
}
