package com.pockettheater.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCode {
    /**
     * A picture of the QR code for `text`: dark squares on white, with a quiet border so phones can read it.
     * Each square is a whole number of pixels, so it stays sharp. The picture is at most `size` pixels wide.
     */
    fun bitmap(text: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, hints)
        val squares = matrix.width
        val scale = maxOf(1, size / squares)
        val side = squares * scale
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint().apply { color = 0xFF14112B.toInt() }
        for (y in 0 until squares) {
            for (x in 0 until squares) {
                if (matrix.get(x, y)) {
                    canvas.drawRect(
                        (x * scale).toFloat(), (y * scale).toFloat(),
                        ((x + 1) * scale).toFloat(), ((y + 1) * scale).toFloat(), ink
                    )
                }
            }
        }
        return bitmap
    }
}
