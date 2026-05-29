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

data class LabelSize(val widthMm: Int, val heightMm: Int, val dpi: Int = 600) {
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
        val w = size.widthPx
        val h = size.heightPx
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // White background with border
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        val border = (w * 0.008).toInt().coerceAtLeast(2)
        g.color = Color(51, 51, 51)
        g.drawRect(border, border, w - border * 2 - 1, h - border * 2 - 1)

        val pad = (h * 0.04).toInt().coerceAtLeast(4)

        // RIGHT SIDE: QR code as large as possible
        val qrPad = pad + border
        val qrSize = h - qrPad * 2
        val qrX = w - qrSize - qrPad
        val qrImage = generateQrCode(bookUrl, qrSize)
        g.drawImage(qrImage, qrX, qrPad, null)

        // LEFT SIDE: everything fits in the space left of the QR
        val leftWidth = qrX - pad - border
        val leftCenter = border + pad + leftWidth / 2

        // Logo
        val logoSize = (h * 0.22).toInt().coerceIn(30, 120)
        var y = pad + border + (h * 0.04).toInt()
        logo?.let {
            val scaled = it.getScaledInstance(logoSize, logoSize, java.awt.Image.SCALE_SMOOTH)
            g.drawImage(scaled, leftCenter - logoSize / 2, y, null)
            y += logoSize + (h * 0.02).toInt()
        }

        // "Ex Libris" text
        val exLibrisSize = (h * 0.065).toFloat().coerceIn(10f, 24f)
        g.color = Color(80, 80, 80)
        g.font = Font("Serif", Font.ITALIC, exLibrisSize.toInt())
        var fm = g.fontMetrics
        val exText = "Ex Libris"
        g.drawString(exText, leftCenter - fm.stringWidth(exText) / 2, y + fm.ascent)
        y += fm.height + 1

        // "Abdulelah" text
        val nameSize = (h * 0.075).toFloat().coerceIn(11f, 26f)
        g.color = Color(40, 40, 40)
        g.font = Font("Serif", Font.BOLD, nameSize.toInt())
        fm = g.fontMetrics
        val nameText = "Abdulelah"
        g.drawString(nameText, leftCenter - fm.stringWidth(nameText) / 2, y + fm.ascent)
        y += fm.height + (h * 0.03).toInt()

        // Divider
        g.color = Color(180, 180, 180)
        val lineInset = (leftWidth * 0.1).toInt()
        g.drawLine(border + pad + lineInset, y, border + pad + leftWidth - lineInset, y)
        y += (h * 0.03).toInt()

        // Book title
        val titleSize = (h * 0.06).toFloat().coerceIn(10f, 22f)
        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.BOLD, titleSize.toInt())
        fm = g.fontMetrics
        val titleLines = wrapText(title, fm, leftWidth)
        val maxLines = ((h - y - pad - border) / fm.height).coerceIn(1, 3)
        for (i in 0 until minOf(titleLines.size, maxLines)) {
            val line = if (i == maxLines - 1 && i < titleLines.size - 1) {
                val t = titleLines[i]
                if (t.length > 3) t.dropLast(3) + "..." else t
            } else titleLines[i]
            val tw = fm.stringWidth(line)
            g.drawString(line, leftCenter - tw / 2, y + fm.ascent)
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
                val safeTitle = title.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim().take(50)
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
