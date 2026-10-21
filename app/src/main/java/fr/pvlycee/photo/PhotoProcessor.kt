package fr.pvlycee.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri

/** Réglages de conversion d'une photo pour le papier thermique. */
data class PhotoSettings(
    val brightness: Int = 10,
    val contrast: Float = 1.1f,
    /** Tramage pour une photo ; seuil simple pour un texte ou un dessin. */
    val dither: Boolean = true,
    /** Rotation supplémentaire choisie par l'utilisateur, en degrés. */
    val rotation: Int = 0,
)

object PhotoProcessor {
    /** Taille maximale conservée en mémoire (les vieux terminaux ont peu de RAM). */
    const val MAX_SOURCE = 1280

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** Réduit l'image pour qu'aucun côté ne dépasse [max] points. */
    fun downscale(src: Bitmap, max: Int = MAX_SOURCE): Bitmap {
        val big = maxOf(src.width, src.height)
        if (big <= max) return src
        val k = max.toFloat() / big
        return Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
    }

    /** Charge une image choisie dans les fichiers, sans la charger en pleine résolution. */
    fun decode(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SOURCE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return downscale(bmp)
    }

    /**
     * Convertit la photo en image noir et blanc pur à la largeur du papier.
     * La hauteur est limitée à deux fois la largeur pour ne pas dérouler trop de papier.
     */
    fun toPrintable(src: Bitmap, widthDots: Int, s: PhotoSettings): Bitmap {
        val rotated = rotate(src, s.rotation)
        val (w, h) = PhotoDither.scaledSize(rotated.width, rotated.height, widthDots, widthDots * 2)
        val scaled = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(scaled).apply {
            drawColor(Color.WHITE)
            // Recadrage centré si la photo est plus haute que la limite.
            val srcH = (rotated.width.toLong() * h / w).toInt().coerceAtMost(rotated.height)
            val top = (rotated.height - srcH) / 2
            drawBitmap(
                rotated,
                android.graphics.Rect(0, top, rotated.width, top + srcH),
                android.graphics.Rect(0, 0, w, h),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(px.size) { PhotoDither.luminance(px[it]) }
        val black = if (s.dither) PhotoDither.floydSteinberg(lum, w, h, s.brightness, s.contrast)
        else PhotoDither.threshold(lum, s.brightness, s.contrast)
        for (i in px.indices) px[i] = if (black[i]) Color.BLACK else Color.WHITE
        scaled.setPixels(px, 0, w, 0, 0, w, h)
        return scaled
    }
}
