package com.areenax.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.util.formatDateTime
import kotlinx.coroutines.launch

/*
 * ResultProofSheet — port of src/components/shared/ResultProofSheet.tsx
 * (SPEC/03 §22). Gallery pick (≤4MB → base64 data URL) with preview card,
 * submitted banner, dashed "Choose from Gallery" tile, submit →
 * POST /tournaments/:id/result-proof {image}. Copy EXACT:
 * toasts "Result proof submitted"/"The admin can now see your screenshot in
 * the Admin Panel." (update: "Result proof updated"); buttons
 * "Upload Screenshot" / "Update Screenshot" / "Uploading…".
 *
 * NOTE: TournamentDetailsScreen implements this inline for /proof (one-time,
 * ≤8MB); builders should reuse [rememberImagePicker] there too.
 */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ResultProofSheet(
    open: Boolean,
    onClose: () -> Unit,
    tournamentId: String,
    env: NavEnv,
    modifier: Modifier = Modifier,
    submittedAt: String? = null,
    onUploaded: () -> Unit = {},
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var image by remember(open) { mutableStateOf<String?>(null) }
    var imageName by remember(open) { mutableStateOf<String?>(null) }
    var uploading by remember(open) { mutableStateOf(false) }
    var hasPrevious by remember(open) { mutableStateOf(!submittedAt.isNullOrBlank()) }
    val picker = rememberImagePicker(UploadCaps.RESULT_PROOF) { picked ->
        if (picked != null) {
            image = picked.dataUrl
            imageName = picked.fileName
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Result Proof",
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hasPrevious && image == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Proof submitted • ${formatDateTime(submittedAt)} — upload a new screenshot to replace it.",
                        style = Type.labelMd,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            image?.let { dataUrl ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        AsyncImage(
                            model = dataUrl,
                            contentDescription = "Screenshot preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Screenshot ready to upload",
                                style = Type.labelLg,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { image = null; imageName = null }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } ?: run {
                // Dashed "Choose from Gallery" tile
                Surface(
                    onClick = { picker.launch() },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_add_photo_alternate),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Choose from Gallery", style = Type.labelLg, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "PNG or JPG screenshot • max 4MB",
                            style = Type.labelMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val dataUrl = image
                    if (dataUrl == null) {
                        env.toast("Please choose a screenshot image", ToastVariant.Destructive)
                        return@Button
                    }
                    uploading = true
                    scope.launch {
                        val result = safeCall {
                            env.api.submitResultProof(
                                tournamentId,
                                com.areenax.app.data.ProofImageRequest(image = dataUrl),
                            )
                        }
                        uploading = false
                        when (result) {
                            is ApiResult.Success -> {
                                hasPrevious = true
                                env.toast.show(
                                    "Result proof submitted",
                                    "The admin can now see your screenshot in the Admin Panel.",
                                )
                                onUploaded()
                                onClose()
                            }
                            is ApiResult.Error -> env.toast(result.message, ToastVariant.Destructive)
                            is ApiResult.NetworkError -> env.toast(result.message, ToastVariant.Destructive)
                        }
                    }
                },
                enabled = !uploading,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (uploading) {
                    AreenaxSpinner(size = 20, strokeWidth = 2)
                    Spacer(Modifier.size(8.dp))
                    Text("Uploading…", style = Type.labelLg)
                } else {
                    Text(
                        if (hasPrevious) "Update Screenshot" else "Upload Screenshot",
                        style = Type.labelLg,
                    )
                }
            }
        }
    }
}
