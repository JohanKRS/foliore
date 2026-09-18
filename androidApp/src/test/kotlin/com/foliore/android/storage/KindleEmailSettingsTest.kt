package com.foliore.android.storage

import kotlin.test.*

class KindleEmailSettingsTest {
    private val smtp = KindleEmailSettings("reader@kindle.com", "sender@example.com", "smtp.example.com", 587, "sender", true)

    @Test fun existingSmtpSettingsRemainUsable() { assertTrue(smtp.isReady) }
    @Test fun gmailNeedsNoSmtpPassword() {
        val gmail = smtp.copy(provider = KindleEmailProvider.GMAIL, gmailAccount = "reader@gmail.com", hasSmtpPassword = false)
        assertTrue(gmail.isReady)
        assertFalse(gmail.copy(kindleEmail = null).isReady)
    }
    @Test fun disconnectingGmailDoesNotSilentlyFallBackToSavedSmtp() {
        assertFalse(smtp.copy(provider = KindleEmailProvider.GMAIL, gmailAccount = null).isReady)
    }
    @Test fun connectedGmailDoesNotSilentlyReplaceChosenSmtp() {
        assertFalse(smtp.copy(gmailAccount = "reader@gmail.com", hasSmtpPassword = false).isReady)
    }
}
