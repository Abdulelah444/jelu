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
        val border = (w * 0.01).toInt().coerceAtLeast(2)
        g.color = Color(51, 51, 51)
        g.drawRect(border, border, w - border * 2 - 1, h - border * 2 - 1)

        val pad = (w * 0.03).toInt()

        // Calculate layout: logo takes ~8%, QR takes as much as possible, title takes rest
        val logoSize = (h * 0.09).toInt().coerceIn(20, 80)
        val titleFontSize = (h * 0.07).toFloat().coerceIn(12f, 28f)
        g.font = Font("Serif", Font.BOLD, titleFontSize.toInt())
        val fm = g.fontMetrics
        val titleLines = wrapText(title, fm, w - pad * 2)
        val titleHeight = fm.height * minOf(titleLines.size, 2) + 4
        val dividerSpace = (h * 0.015).toInt()

        // QR gets all remaining space
        val qrAvailable = h - pad * 2 - logoSize - 4 - dividerSpace * 2 - titleHeight
        val qrSize = minOf(qrAvailable, w - pad * 2).coerceAtLeast(60)

        var y = pad

        // Logo centered
        logo?.let {
            val scaled = it.getScaledInstance(logoSize, logoSize, java.awt.Image.SCALE_SMOOTH)
            g.drawImage(scaled, (w - logoSize) / 2, y, null)
            y += logoSize + 2
        }

        // QR Code centered
        val qrImage = generateQrCode(bookUrl, qrSize)
        g.drawImage(qrImage, (w - qrSize) / 2, y, null)
        y += qrSize + dividerSpace

        // Divider line
        g.color = Color(180, 180, 180)
        val lineInset = (w * 0.08).toInt()
        g.drawLine(lineInset, y, w - lineInset, y)
        y += dividerSpace

        // Title centered
        g.color = Color.BLACK
        g.font = Font("Serif", Font.BOLD, titleFontSize.toInt())
        val maxLines = 2
        for (i in 0 until minOf(titleLines.size, maxLines)) {
            val line = if (i == maxLines - 1 && i < titleLines.size - 1) {
                val truncated = titleLines[i]
                if (truncated.length > 3) truncated.dropLast(3) + "..." else truncated
            } else titleLines[i]
            val tw = fm.stringWidth(line)
            g.drawString(line, (w - tw) / 2, y + fm.ascent)
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
