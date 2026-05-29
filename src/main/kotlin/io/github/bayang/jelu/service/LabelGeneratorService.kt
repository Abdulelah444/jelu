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

        // White background with outer border
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        val border = (w * 0.008).toInt().coerceAtLeast(2)
        g.color = Color(51, 51, 51)
        for (i in 0 until border) {
            g.drawRect(i, i, w - i * 2 - 1, h - i * 2 - 1)
        }

        val outerPad = (h * 0.05).toInt().coerceAtLeast(6)

        // QR takes 80% of height, leaving breathing room
        val qrSize = (h * 0.80).toInt()
        val qrPad = (h * 0.015).toInt().coerceAtLeast(2)
        val qrBoxSize = qrSize + qrPad * 2
        val qrBoxX = w - border - outerPad - qrBoxSize
        val qrBoxY = (h - qrBoxSize) / 2

        // QR code
        val qrImage = generateQrCode(bookUrl, qrSize)
        g.drawImage(qrImage, qrBoxX + qrPad, qrBoxY + qrPad, null)

        // LEFT SIDE: from left edge to just before QR box, small gap
        val leftGap = (w * 0.02).toInt().coerceAtLeast(4)
        val leftStart = border + outerPad
        val leftEnd = qrBoxX - leftGap
        val leftWidth = leftEnd - leftStart
        val leftCenter = leftStart + (leftWidth * 0.6).toInt()

        // Pre-calculate sizes
        val logoSize = (h * 0.26).toInt().coerceIn(30, 130)
        val exLibrisSize = (h * 0.08).toFloat().coerceIn(12f, 28f)
        val nameSize = (h * 0.09).toFloat().coerceIn(13f, 30f)
        val titleSize = (h * 0.075).toFloat().coerceIn(12f, 26f)
        val gap = (h * 0.015).toInt().coerceAtLeast(2)
        val divGap = (h * 0.02).toInt().coerceAtLeast(3)

        g.font = Font("Serif", Font.ITALIC, exLibrisSize.toInt())
        val exFm = g.fontMetrics
        g.font = Font("Serif", Font.BOLD, nameSize.toInt())
        val nameFm = g.fontMetrics
        g.font = Font("SansSerif", Font.BOLD, titleSize.toInt())
        val titleFm = g.fontMetrics
        val titleLines = wrapText(title, titleFm, leftWidth - 4)
        val maxLines = minOf(titleLines.size, 3)
        val titleTotalH = titleFm.height * maxLines

        val totalContentH = logoSize + gap + exFm.height + 1 + nameFm.height + divGap + 1 + divGap + titleTotalH
        var y = (h - totalContentH) / 2

        // Logo
        logo?.let {
            val scaled = it.getScaledInstance(logoSize, logoSize, java.awt.Image.SCALE_SMOOTH)
            g.drawImage(scaled, leftCenter - logoSize / 2, y, null)
            y += logoSize + gap
        }

        // "Ex Libris"
        g.color = Color(80, 80, 80)
        g.font = Font("Serif", Font.ITALIC, exLibrisSize.toInt())
        val exText = "Ex Libris"
        g.drawString(exText, leftCenter - exFm.stringWidth(exText) / 2, y + exFm.ascent)
        y += exFm.height + 1

        // "Abdulelah"
        g.color = Color(40, 40, 40)
        g.font = Font("Serif", Font.BOLD, nameSize.toInt())
        val nameText = "Abdulelah"
        g.drawString(nameText, leftCenter - nameFm.stringWidth(nameText) / 2, y + nameFm.ascent)
        y += nameFm.height + divGap

        // Divider
        g.color = Color(180, 180, 180)
        val lineInset = (leftWidth * 0.06).toInt()
        g.drawLine(leftStart + lineInset, y, leftEnd - lineInset, y)
        y += divGap

        // Book title
        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.BOLD, titleSize.toInt())
        for (i in 0 until maxLines) {
            val line = if (i == maxLines - 1 && i < titleLines.size - 1) {
                val t = titleLines[i]
                if (t.length > 3) t.dropLast(3) + "..." else t
            } else titleLines[i]
            val tw = titleFm.stringWidth(line)
            g.drawString(line, leftCenter - tw / 2, y + titleFm.ascent)
            y += titleFm.height
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
