@file:OptIn(ExperimentalMaterial3Api::class)

package com.smartscan.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.request.ImageRequest
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import java.io.File

@Composable
fun fileModel(file: File?): ImageRequest? {
    val ctx = LocalContext.current
    val stamp = file?.lastModified() ?: 0L
    return remember(file?.path, stamp) {
        file?.let {
            ImageRequest.Builder(ctx).data(it).memoryCacheKey("${it.path}#$stamp").crossfade(true).build()
        }
    }
}

@Composable
fun Banner() {
    Box(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(factory = { c: Context ->
            AdView(c).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdManager.BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        })
    }
}

@Composable
fun BusyOverlay(message: String) {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(32.dp)) {
            Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(message)
            }
        }
    }
}

private data class ExportOption(val kind: ExportKind, val icon: ImageVector, val title: String, val sub: String)

private val exportOptions = listOf(
    ExportOption(ExportKind.PDF, Icons.Default.PictureAsPdf, "PDF", "Save and share the document"),
    ExportOption(ExportKind.WORD, Icons.Default.Description, "Word (.docx)", "Editable text from OCR"),
    ExportOption(ExportKind.EXCEL_TEXT, Icons.Default.TableChart, "Excel - all text (.xlsx)", "One row per text line"),
    ExportOption(ExportKind.EXCEL_FIELDS, Icons.Default.Tune, "Excel - smart fields", "Invoice no, date, amount, custom fields"),
    ExportOption(ExportKind.JPEG, Icons.Default.Image, "JPEG images", "Save to gallery and share pages"),
    ExportOption(ExportKind.TEXT, Icons.Default.TextSnippet, "Plain text (.txt)", "Recognised text only")
)

@Composable
fun ExportSheet(count: Int, onPick: (ExportKind) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                if (count > 1) "Export $count scans" else "Export as",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            exportOptions.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(o.kind) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(o.icon, null, tint = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(o.title, style = MaterialTheme.typography.titleMedium)
                        Text(o.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
