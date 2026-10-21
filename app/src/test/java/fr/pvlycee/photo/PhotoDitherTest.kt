package fr.pvlycee.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDitherTest {

    @Test fun `blanc reste blanc et noir reste noir`() {
        val w = 16; val h = 16
        assertTrue(PhotoDither.floydSteinberg(IntArray(w * h) { 255 }, w, h).none { it })
        assertTrue(PhotoDither.floydSteinberg(IntArray(w * h) { 0 }, w, h).all { it })
    }

    @Test fun `un gris moyen donne environ moitie de points noirs`() {
        val w = 64; val h = 64
        val out = PhotoDither.floydSteinberg(IntArray(w * h) { 128 }, w, h)
        val ratio = out.count { it }.toDouble() / out.size
        assertEquals(0.5, ratio, 0.05)
    }

    @Test fun `la densite de points suit la luminosite`() {
        val w = 64; val h = 64
        fun density(v: Int) = PhotoDither.floydSteinberg(IntArray(w * h) { v }, w, h).count { it }.toDouble() / (w * h)
        assertEquals(0.75, density(64), 0.05)
        assertEquals(0.25, density(192), 0.05)
        assertTrue(density(64) > density(128) && density(128) > density(192))
    }

    @Test fun `luminosite et contraste`() {
        assertEquals(148, PhotoDither.adjust(128, 20, 1f))
        assertEquals(255, PhotoDither.adjust(250, 50, 1f))
        assertEquals(0, PhotoDither.adjust(10, -50, 1f))
        assertEquals(64, PhotoDither.adjust(96, 0, 2f))
        val lighter = PhotoDither.floydSteinberg(IntArray(1024) { 128 }, 32, 32, brightness = 40).count { it }
        val normal = PhotoDither.floydSteinberg(IntArray(1024) { 128 }, 32, 32).count { it }
        assertTrue(lighter < normal)
    }

    @Test fun `seuil simple`() {
        assertEquals(listOf(true, true, false, false), PhotoDither.threshold(intArrayOf(0, 127, 128, 255)).toList())
    }

    @Test fun `luminance perçue`() {
        assertEquals(255, PhotoDither.luminance(0xFFFFFFFF.toInt()))
        assertEquals(0, PhotoDither.luminance(0xFF000000.toInt()))
        assertEquals(255, PhotoDither.luminance(0x00000000))
        assertEquals(76, PhotoDither.luminance(0xFFFF0000.toInt()))
    }

    @Test fun `dimensions a la largeur du papier`() {
        assertEquals(384 to 288, PhotoDither.scaledSize(1280, 960, 384, 768))
        assertEquals(384 to 768, PhotoDither.scaledSize(960, 4000, 384, 768))
        assertEquals(576 to 432, PhotoDither.scaledSize(1280, 960, 576, 1152))
    }
}
