package com.foliore.android.ai

import com.foliore.android.storage.readBounded
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Random
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenAiHttpTest {
    @Test
    fun portraitPngResponseFitsTheImageLimit() {
        val width = 1024
        val height = 1536
        val random = Random(42)
        val pixels = IntArray(width * height) { random.nextInt() }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        val png = ByteArrayOutputStream().use { out ->
            assertTrue(ImageIO.write(image, "png", out))
            out.toByteArray()
        }
        val encoded = Base64.getEncoder().encodeToString(png)
        val response = """{"data":[{"b64_json":"$encoded"}]}""".toByteArray()

        assertFailsWith<IllegalArgumentException> { response.inputStream().readBounded(OpenAiHttp.MAX_BODY_BYTES) }
        assertContentEquals(response, response.inputStream().readBounded(OpenAiHttp.MAX_IMAGE_RESPONSE_BYTES))
    }
}
