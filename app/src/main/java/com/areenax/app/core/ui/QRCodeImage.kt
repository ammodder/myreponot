package com.areenax.app.core.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * QR generation — ZXing core port of the web `qrcode` lib usage
 * (SPEC/00 §2i): 512px, margin 2, dark #0b1c30 on white.
 */

/** Renders a BitMatrix into a Bitmap with the exact web colors (bulk pixel
 *  writes via setPixels — ~1 call instead of 262k setPixel calls). */
fun bitMatrixToBitmap(matrix: com.google.zxing.common.BitMatrix): Bitmap {
    val width = matrix.width
    val height = matrix.height
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val dark = "#0b1c30".toColorInt()
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            pixels[row + x] = if (matrix.get(x, y)) dark else AndroidColor.WHITE
        }
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}

/** Encode [content] to a 512px QR Bitmap (margin 2, #0b1c30/white). */
fun generateQrBitmap(content: String, size: Int = 512, margin: Int = 2): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to margin,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    return bitMatrixToBitmap(matrix)
}

/** QR image composable (QrSheet "My QR" tile). A7-06: the 512×512 encode ran
 *  synchronously inside `remember {}` during composition (visible frame drop
 *  on low-end devices) — it now runs once on Dispatchers.Default via
 *  produceState. */
@Composable
fun QRCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Int = 512,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, content, size) {
        value = withContext(Dispatchers.Default) {
            runCatching { generateQrBitmap(content, size) }.getOrNull()
        }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "QR code",
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            Box(
                Modifier
                    .background(Color.White)
                    .padding(8.dp),
            )
        }
    }
}
