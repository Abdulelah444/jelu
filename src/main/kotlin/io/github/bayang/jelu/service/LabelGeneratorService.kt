package io.github.bayang.jelu.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

data class LabelSize(val widthMm: Int, val heightMm: Int, val dpi: Int = 203) {
    val widthPx: Int get() = (widthMm * dpi / 25.4).toInt()
    val heightPx: Int get() = (heightMm * dpi / 25.4).toInt()
}

@Service
class LabelGeneratorService {

    private val logo: BufferedImage? by lazy {
        try {
            val resource = ClassPathResource("public/android-chrome-192x192.png")
            ImageIO.read(resource.inputStream)
        } catch (e: Exception) {
            null
        }
    }

    fun generateLabel(title: String, bookUrl: String, size: LabelSize): ByteArray {
        val img = BufferedImage(size.widthPx, size.heightPx, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // White background
        g.color = Color.WHITE
        g.fillRect(0, 0, size.widthPx, size.heightPx)

        // Border
        g.color = Color(51, 51, 51)
        g.drawRect(2, 2, size.widthPx - 5, size.heightPx - 5)

        var y = 8

        // Logo
        val logoSize = (size.heightPx * 0.14).toInt().coerceIn(20, 48)
        logo?.let {
            val scaled = it.getScaledInstance(logoSize, logoSize, java.awt.Image.SCALE_SMOOTH)
            g.drawImage(scaled, (size.widthPx - logoSize) / 2, y, null)
            y += logoSize + 4
        }

        // QR Code
        val qrSize = (size.heightPx * 0.55).toInt().coerceIn(60, size.widthPx - 20)
        val qrImage = generateQrCode(bookUrl, qrSize)
        g.drawImage(qrImage, (size.widthPx - qrSize) / 2, y, null)
        y += qrSize + 4

        // Divider
        g.color = Color(200, 200, 200)
        g.drawLine(size.widthPx / 6, y, size.widthPx * 5 / 6, y)
        y += 6

        // Title
        g.color = Color.BLACK
        val fontSize = (size.heightPx * 0.065).toFloat().coerceIn(8f, 16f)
        g.font = Font("Serif", Font.BOLD, fontSize.toInt())
        val fm = g.fontMetrics
        val maxWidth = size.widthPx - 16
        val lines = wrapText(title, fm, maxWidth)
        val maxLines = ((size.heightPx - y - 4) / fm.height).coerceAtLeast(1)
        for (i in 0 until minOf(lines.size, maxLines)) {
            val line = if (i == maxLines - 1 && i < lines.size - 1) lines[i].dropLast(3) + "..." else lines[i]
            val tw = fm.stringWidth(line)
            g.drawString(line, (size.widthPx - tw) / 2, y + fm.ascent)
            y += fm.height
        }

        g.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "PNG", baos)
        return baos.toByteArray()
    }

    fun generateBulkZip(books: List<Pair<String, String>>, size: LabelSize): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            for ((title, url) in books) {
                val safeTitle = title.replace(Regex("[^\\w\\s-]"), "").trim().take(50)
                val label = generateLabel(title, url, size)
                zip.putNextEntry(ZipEntry("$safeTitle.png"))
                zip.write(label)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun generateQrCode(data: String, size: Int): BufferedImage {
        val writer = QRCodeWriter()
        val matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                img.setRGB(x, y, if (matrix.get(x, y)) Color.BLACK.rgb else Color.WHITE.rgb)
            }
        }
        return img
    }

    private fun wrapText(text: String, fm: java.awt.FontMetrics, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (fm.stringWidth(test) <= maxWidth) {
                current = test
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }
}
