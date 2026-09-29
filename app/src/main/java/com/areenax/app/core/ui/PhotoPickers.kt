package com.areenax.app.core.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * Photo Picker + base64 data-URL helpers — the native equivalent of the web
 * FileReader flow (SPEC/00 §3 uploads row, SPEC/02 upload caps):
 *   deposit receipt        ≤ 8 MB   (POST /wallet/deposit)
 *   result proof           ≤ 8 MB   (POST /tournaments/:id/result-proof) — Q3
 *   host tournament image  ≤ 5 MB   (POST /tournaments)
 *   avatar                 ≤ 8 MB   (PATCH /me)
 *
 * Picked images are sent as the ORIGINAL bytes (`data:<mime>;base64,…`),
 * exactly like the web sends the raw file. Q2/Q3 (owner decision): a pick
 * over the cap is REFUSED with an error message — the previous silent
 * downscale behavior diverged from the web (REQ-143) and could change what
 * the reviewer/admin sees on a real-money path.
 */

/** Endpoint caps (bytes) — the RAW image file sizes the server validates. */
object UploadCaps {
    const val RECEIPT: Int = 8 * 1024 * 1024
    const val RESULT_PROOF: Int = 8 * 1024 * 1024 // Q3: was 4 MB dead-code/8 MB live mix — now 8 MB everywhere
    const val HOST_IMAGE: Int = 5 * 1024 * 1024
    const val AVATAR: Int = 8 * 1024 * 1024
}

data class PickedImage(
    val dataUrl: String,
    val fileName: String,
    val bytes: Int,
)

internal fun readDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(nameIndex)
            if (!name.isNullOrBlank()) return name
        }
    }
    return "image.jpg"
}

internal fun encodeJpegDataUrl(bytes: ByteArray): String =
    "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

/** Formats a byte cap as a whole-MB user string ("8 MB"). */
internal fun capLabel(capBytes: Int): String = "${(capBytes + (1024 * 1024) - 1) / (1024 * 1024)} MB"

/**
 * Stateful image-pick hook returning a base64 data URL within [capBytes].
 * `onPicked(null)` fires when the user cancels; `onError` fires when the pick
 * is REFUSED for exceeding the cap (Q2 — same contract as the web's
 * rejection message; no silent downscaling). [onPicked] is the trailing
 * lambda for readability at call sites.
 *
 * ```kotlin
 * val picker = rememberImagePicker(UploadCaps.RESULT_PROOF, onError = { msg -> toast(msg) }) { picked -> ... }
 * Button(onClick = { picker.launch() }) { Text("Choose from Gallery") }
 * ```
 */
@Composable
fun rememberImagePicker(
    capBytes: Int,
    onError: (String) -> Unit = {},
    onPicked: (PickedImage?) -> Unit,
): ImagePickerHandle {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        val name = readDisplayName(context, uri)
        val rawBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
        if (rawBytes == null) {
            onError("Couldn't read the selected image. Please try another file.")
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        if (rawBytes.size > capBytes) {
            // Q2/Q3: refuse over-cap picks exactly like the web — the silent
            // JPEG-downscale path was removed (REQ-143 divergence).
            onError("Image is too large (max ${capLabel(capBytes)}). Please choose a smaller file.")
            onPicked(null)
            return@rememberLauncherForActivityResult
        }
        // Within cap — send the ORIGINAL bytes (web sends the raw file).
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val dataUrl = "data:$mime;base64," + Base64.encodeToString(rawBytes, Base64.NO_WRAP)
        onPicked(PickedImage(dataUrl, name, rawBytes.size))
    }
    return remember {
        ImagePickerHandle {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** Launcher wrapper so screens can call `picker.launch()` from a click. */
class ImagePickerHandle internal constructor(private val launchBlock: () -> Unit) {
    fun launch() = launchBlock()
}

/** Read a picked file's extension for receiptName-style fields. */
fun Uri.fileExtension(context: Context): String {
    val name = readDisplayName(context, this)
    val dot = name.lastIndexOf('.')
    return if (dot >= 0) name.substring(dot + 1) else "jpg"
}
