package com.foliore.android.storage

import java.io.IOException
import kotlin.test.*

class DriveBatchDeleteTest {
    private fun response(vararg parts: Pair<Int, Int>, closed: Boolean = true): String = parts.joinToString("") { (id, status) ->
        "--batch\r\nContent-Type: application/http\r\nContent-ID: <response-$id>\r\n\r\nHTTP/1.1 $status Result\r\nContent-Type: application/json\r\n\r\n{\"reason\":\"backendError\"}\r\n"
    } + if (closed) "--batch--\r\n" else ""

    @Test fun mapsEveryResponseAndKeepsRetryableErrorsVisible() {
        val parsed = parseDriveDeleteBatch("multipart/mixed; boundary=\"batch\"", response(2 to 503, 0 to 204, 1 to 404), 3)
        assertEquals(listOf(0, 1, 2), parsed.map { it.index })
        assertTrue(parsed[0].succeeded); assertTrue(parsed[1].succeeded); assertFalse(parsed[2].succeeded)
        assertTrue(GoogleDriveHttpException(parsed[2].status, parsed[2].body).isRetryable)
    }

    @Test fun successEnvelopeCannotHideMissingDuplicatedOrTruncatedParts() {
        for (body in listOf(response(0 to 204), response(0 to 204, 0 to 204), response(0 to 204, 2 to 204),
            response(0 to 204, 1 to 204, closed = false), "{}")) {
            assertFailsWith<IOException> { parseDriveDeleteBatch("multipart/mixed; boundary=batch", body, 2) }
        }
        assertFailsWith<IOException> { parseDriveDeleteBatch("application/json", response(0 to 204), 1) }
    }

    @Test fun authenticationAndQuotaFailuresRemainFailures() {
        val parts = parseDriveDeleteBatch("multipart/mixed; boundary=batch", response(0 to 401, 1 to 403), 2)
        assertTrue(parts.none { it.succeeded })
        assertTrue(GoogleDriveHttpException(parts[0].status, parts[0].body).isAuthorizationError)
    }
}
