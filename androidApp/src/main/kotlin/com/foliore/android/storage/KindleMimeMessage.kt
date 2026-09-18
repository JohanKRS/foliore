package com.foliore.android.storage

import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

/** Stream attachments directly for both transports, with a fixed-size copy buffer. */
internal object KindleMimeMessage {
    const val MAX_ATTACHMENT_BYTES = 25_000_000L
    fun validAddress(value: String) = value.length in 3..254 &&
        value.count { it == '@' } == 1 && !value.startsWith('@') && !value.endsWith('@') &&
        value.none { it.isWhitespace() || it.isISOControl() || it in "<>,;:\"\\" }

    fun write(output: OutputStream, from: String, to: String, subject: String, body: String, attachments: List<File>) {
        require(validAddress(from) && validAddress(to))
        require(attachments.isNotEmpty() && attachments.size <= 25)
        require(attachments.sumOf { it.length() } <= MAX_ATTACHMENT_BYTES)
        val boundary = "foliore-${UUID.randomUUID()}"
        fun line(value: String = "") { output.write((value + "\r\n").toByteArray(Charsets.UTF_8)) }
        fun base64(write: (OutputStream) -> Unit) {
            val keepOpen = object : FilterOutputStream(output) {
                override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                override fun close() = flush()
            }
            Base64.getMimeEncoder(76, "\r\n".toByteArray()).wrap(keepOpen).use(write)
            line()
        }
        line("From: <$from>"); line("To: <$to>")
        val cleanSubject = subject.replace(Regex("[\\r\\n]"), " ")
        val subjectChunks = cleanSubject.codePoints().toArray().take(250).chunked(10).map { String(it.toIntArray(), 0, it.size) }
        subjectChunks.forEachIndexed { index, chunk ->
            line((if (index == 0) "Subject: " else " ") + "=?UTF-8?B?${Base64.getEncoder().encodeToString(chunk.toByteArray(Charsets.UTF_8))}?=")
        }
        line("MIME-Version: 1.0"); line("Content-Type: multipart/mixed; boundary=\"$boundary\""); line()
        line("--$boundary"); line("Content-Type: text/plain; charset=UTF-8"); line("Content-Transfer-Encoding: base64"); line()
        base64 { it.write(body.toByteArray(Charsets.UTF_8)) }
        var total = 0L
        attachments.forEach { attachment ->
            val name = attachment.name.replace(Regex("[\\r\\n\"\\\\]"), "_")
            val ascii = name.map { if (it.code in 32..126) it else '_' }.joinToString("").take(120)
            val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
            line("--$boundary")
            line("Content-Type: application/epub+zip")
            line("Content-Disposition: attachment;")
            line(" filename=\"$ascii\";")
            if (encoded.length <= 600) {
                line(" filename*=UTF-8''$encoded")
            } else {
                // RFC 2231 continuations keep long Unicode filenames below SMTP's line limit.
                val chunks = Regex("%[0-9A-Fa-f]{2}|.").findAll(encoded).map { it.value }.toList().chunked(16)
                chunks.forEachIndexed { index, chunk ->
                    line(" filename*$index*=" + (if (index == 0) "UTF-8''" else "") +
                        chunk.joinToString("") + if (index == chunks.lastIndex) "" else ";")
                }
            }
            line("Content-Transfer-Encoding: base64")
            line()
            base64 { target ->
                attachment.inputStream().use { source ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ATTACHMENT_BYTES)
                        target.write(buffer, 0, count)
                    }
                }
            }
        }
        line("--$boundary--")
    }
}
