package net.spotlive.player.piattaforma

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import android.webkit.WebView
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object IstantaneaSchermo {
    fun cattura(activity: Activity, webView: WebView, larghezzaMax: Int = 0): Pair<String, String> {
        val originale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                catturaPixelCopy(activity, webView) ?: disegnaView(webView)
            } else {
                disegnaView(webView)
            }
        val bitmap =
            if (larghezzaMax > 0 && originale.width > larghezzaMax) {
                val h = (originale.height.toLong() * larghezzaMax / originale.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originale, larghezzaMax, h, true).also { originale.recycle() }
            } else {
                originale
            }
        val uscita = ByteArrayOutputStream()
        // Con larghezzaMax: WEBP leggero (sta nel limite di 1 MB del server); senza: PNG come prima.
        val mime =
            if (larghezzaMax > 0) {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 75, uscita)
                "image/webp"
            } else {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, uscita)
                "image/png"
            }
        if (!bitmap.isRecycled) bitmap.recycle()
        val base64 = Base64.encodeToString(uscita.toByteArray(), Base64.NO_WRAP)
        return Pair(mime, base64)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun catturaPixelCopy(activity: Activity, webView: WebView): Bitmap? {
        if (webView.width <= 0 || webView.height <= 0) return null
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        val errore = AtomicReference<Int?>(null)
        val handler = Handler(Looper.getMainLooper())
        PixelCopy.request(
            activity.window,
            bitmap,
            PixelCopy.OnPixelCopyFinishedListener { result ->
                if (result != PixelCopy.SUCCESS) {
                    errore.set(result)
                }
                latch.countDown()
            },
            handler,
        )
        latch.await(3, TimeUnit.SECONDS)
        return if (errore.get() == null) bitmap else {
            bitmap.recycle()
            null
        }
    }

    private fun disegnaView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
