package fr.pvlycee.print

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EscPosEncoderTest {

    @Test fun `en-tete GS v 0 et donnees bits de poids fort en premier`() {
        val img = MonoImage(384, 2)
        img[0, 0] = true; img[9, 0] = true; img[383, 1] = true
        val band = EscPosEncoder.rasterBand(img, 0, 64)
        assertArrayEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 48, 0, 2, 0), band.copyOfRange(0, 8))
        assertEquals(8 + 48 * 2, band.size)
        assertEquals(0x80.toByte(), band[8])        // point 0
        assertEquals(0x40.toByte(), band[9])        // point 9
        assertEquals(0x01.toByte(), band[8 + 48 + 47]) // point 383, ligne 2
    }

    @Test fun `largeur 80 mm, 72 octets par ligne`() {
        val band = EscPosEncoder.rasterBand(MonoImage(576, 1), 0, 64)
        assertEquals(72.toByte(), band[4])
        assertEquals(0.toByte(), band[5])
    }

    @Test fun `largeur non multiple de 8 arrondie a l'octet superieur`() {
        val img = MonoImage(10, 1)
        img[9, 0] = true
        val band = EscPosEncoder.rasterBand(img, 0, 64)
        assertEquals(2.toByte(), band[4])
        assertEquals(0x40.toByte(), band[9])
    }

    @Test fun `decoupage en bandes de 64 lignes`() {
        val img = MonoImage(384, 130)
        val parts = EscPosEncoder.encode(img, 64, feed = 3)
        assertArrayEquals(EscPosEncoder.INIT, parts.first())
        val bands = parts.drop(1).dropLast(1)
        assertEquals(3, bands.size)
        assertEquals(listOf(64, 64, 2), bands.map { (it[6].toInt() and 0xFF) or ((it[7].toInt() and 0xFF) shl 8) })
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 3), parts.last())
    }

    @Test fun `hauteur superieure a 255 lignes codee sur deux octets`() {
        val band = EscPosEncoder.rasterBand(MonoImage(8, 300), 0, 300)
        assertEquals(44.toByte(), band[6])
        assertEquals(1.toByte(), band[7])
    }
}
