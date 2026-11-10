package com.example.tripcountdown

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.File

fun isPdf(path: String): Boolean = path.endsWith(".pdf", ignoreCase = true)

/** Draws the first page of a PDF as a small picture. Returns null if it can't be read. */
fun pdfThumb(path: String, width: Int): ImageBitmap? {
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null
    var result: ImageBitmap? = null
    try {
        pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        page = renderer.openPage(0)
        val h = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        result = bmp.asImageBitmap()
    } catch (e: Exception) {
        result = null
    } finally {
        try {
            page?.close()
            renderer?.close()
            pfd?.close()
        } catch (e: Exception) {
        }
    }
    return result
}

/** Opens a saved PDF in the phone's PDF viewer. */
fun openPdf(ctx: Context, path: String) {
    try {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(path))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app found to open PDFs", Toast.LENGTH_LONG).show()
    }
}
