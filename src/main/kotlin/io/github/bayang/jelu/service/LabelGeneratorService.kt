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
        val border = (w * 0.01).toInt().coerceAtLeast(2)
        g.color = Color(51, 51, 51)
        for (i in 0 until border) {
            g.drawRect(i, i, w - i * 2 - 1, h - i * 2 - 1)
        }

        val outerPad = (h * 0.06).toInt().coerceAtLeast(8)

        // Layout: 50% left, 50% right (QR) with generous left margin
        val rightWidth = (w * 0.48).toInt()
        val leftWidth = w - rightWidth

        // RIGHT SIDE: QR code with border, centered in right area
        val qrMargin = outerPad
        val qrAvailH = h - qrMargin * 2
        val qrAvailW = rightWidth - outerPad - (w * 0.02).toInt()
        val qrBoxInner = minOf(qrAvailH, qrAvailW)
        val qrPad = (qrBoxInner * 0.04).toInt().coerceAtLeast(4)
        val qrBoxOuter = qrBoxInner + qrPad * 2
        val qrBoxX = leftWidth + (w * 0.01).toInt()
        val qrBoxY = (h - qrBoxOuter) / 2

        // QR border
        g.color = Color(80, 80, 80)
        val qrBorderW = (w * 0.005).toInt().coerceAtLeast(2)
        for (i in 0 until qrBorderW) {
            g.drawRect(qrBoxX + i, qrBoxY + i, qrBoxOuter - i * 2 - 1, qrBoxOuter - i * 2 - 1)
        }

        // QR code
        val qrImage = generateQrCode(bookUrl, qrBoxInner)
        g.drawImage(qrImage, qrBoxX + qrPad, qrBoxY + qrPad, null)

        // LEFT SIDE: all content centered in left area
        val leftPad = outerPad + (w * 0.05).toInt()
        val leftContentW = leftWidth - leftPad * 2
        val leftCenterX = leftPad + leftContentW / 2

        // Size calculations
        val logoSize = (h * 0.28).toInt().coerceIn(40, 160)
        val exSize = (h * 0.085).toFloat().coerceIn(14f, 32f)
        val nameSize = (h * 0.1).toFloat().coerceIn(16f, 36f)
        val titleSize = (h * 0.08).toFloat().coerceIn(12f, 28f)
        val gap = (h * 0.015).toInt().coerceAtLeast(2)

        g.font = Font("Serif", Font.ITALIC, exSize.toInt())
        val exFm = g.fontMetrics
        g.font = Font("Serif", Font.BOLD, nameSize.toInt())
        val nameFm = g.fontMetrics
        g.font = Font("SansSerif", Font.BOLD, titleSize.toInt())
        val titleFm = g.fontMetrics
        val titleLines = wrapText(title, titleFm, leftContentW)
        val maxTitleLines = minOf(titleLines.size, 2)
        val titleH = titleFm.height * maxTitleLines
        val divH = (h * 0.02).toInt().coerceAtLeast(3)

        val totalH = logoSize + gap + exFm.height + gap / 2 + nameFm.height + divH * 2 + 1 + titleH
        var y = (h - totalH) / 2

        // Logo
        logo?.let {
            val scaled = it.getScaledInstance(logoSize, logoSize, java.awt.Image.SCALE_SMOOTH)
            g.drawImage(scaled, leftCenterX - logoSize / 2, y, null)
            y += logoSize + gap
        }

        // "Ex Libris"
        g.color = Color(80, 80, 80)
        g.font = Font("Serif", Font.ITALIC, exSize.toInt())
        val exText = "Ex Libris"
        g.drawString(exText, leftCenterX - exFm.stringWidth(exText) / 2, y + exFm.ascent)
        y += exFm.height + gap / 2

        // "Abdulelah"
        g.color = Color(30, 30, 30)
        g.font = Font("Serif", Font.BOLD, nameSize.toInt())
        val nameText = "Abdulelah"
        g.drawString(nameText, leftCenterX - nameFm.stringWidth(nameText) / 2, y + nameFm.ascent)
        y += nameFm.height + divH

        // Divider
        g.color = Color(160, 160, 160)
        val lineInset = (leftContentW * 0.05).toInt()
        val lineStroke = (h * 0.004).toInt().coerceAtLeast(1)
        for (i in 0 until lineStroke) {
            g.drawLine(leftPad + lineInset, y + i, leftPad + leftContentW - lineInset, y + i)
        }
        y += divH + lineStroke

        // Book title
        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.BOLD, titleSize.toInt())
        for (i in 0 until maxTitleLines) {
            val line = if (i == maxTitleLines - 1 && i < titleLines.size - 1) {
                val t = titleLines[i]
                if (t.length > 3) t.dropLast(3) + "..." else t
            } else titleLines[i]
            val tw = titleFm.stringWidth(line)
            g.drawString(line, leftCenterX - tw / 2, y + titleFm.ascent)
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
