package fr.pvlycee.print

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatPrinterEncoderTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()
    private fun ints(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun `CRC-8 polynome 0x07 (valeurs de reference)`() {
        assertEquals(0x00, CatPrinterEncoder.crc8(ints(0x00)))
        assertEquals(0x07, CatPrinterEncoder.crc8(ints(0x01)))
        assertEquals(0x9E, CatPrinterEncoder.crc8(ints(0x32)))
        assertEquals(0xF9, CatPrinterEncoder.crc8(ints(0x30, 0x00)))
        // Vecteur standard CRC-8/SMBUS : "123456789" -> 0xF4
        assertEquals(0xF4, CatPrinterEncoder.crc8("123456789".toByteArray()))
    }

    @Test fun `structure d'un paquet`() {
        assertArrayEquals(hex("51 78 a4 00 01 00 32 9e ff"), CatPrinterEncoder.quality200Dpi())
        assertArrayEquals(hex("51 78 a3 00 01 00 00 00 ff"), CatPrinterEncoder.getState())
        assertArrayEquals(hex("51 78 a1 00 02 00 30 00 f9 ff"), CatPrinterEncoder.feed(0x30))
        assertArrayEquals(hex("51 78 be 00 01 00 01 07 ff"), CatPrinterEncoder.applyEnergy())
        assertArrayEquals(
            hex("51 78 a6 00 0b 00 aa 55 17 38 44 5f 5f 5f 44 38 2c a1 ff"),
            CatPrinterEncoder.latticeStart(),
        )
        assertArrayEquals(
            hex("51 78 a6 00 0b 00 aa 55 17 00 00 00 00 00 00 00 17 11 ff"),
            CatPrinterEncoder.latticeEnd(),
        )
        val e = CatPrinterEncoder.energy(0x5DC0)
        assertEquals(0xC0.toByte(), e[6]); assertEquals(0x5D.toByte(), e[7])
        assertEquals(CatPrinterEncoder.crc8(ints(0xC0, 0x5D)).toByte(), e[8])
    }

    @Test fun `ligne 0xA2 de 48 octets, bits de poids faible en premier`() {
        val img = MonoImage(384, 1)
        img[0, 0] = true; img[7, 0] = true; img[8, 0] = true; img[383, 0] = true
        val p = CatPrinterEncoder.drawLine(img, 0)
        assertEquals(8 + 48, p.size)
        assertEquals(0xA2.toByte(), p[2])
        assertEquals(48.toByte(), p[4])
        assertEquals(0x81.toByte(), p[6])      // points 0 et 7
        assertEquals(0x01.toByte(), p[7])      // point 8
        assertEquals(0x80.toByte(), p[6 + 47]) // point 383
        assertEquals(CatPrinterEncoder.crc8(p, 6, 48).toByte(), p[6 + 48])
        assertEquals(0xFF.toByte(), p.last())
    }

    @Test fun `sequence complete, init, energie, lignes, avance, fin`() {
        val img = MonoImage(384, 3)
        val parts = CatPrinterEncoder.encode(img, intensity = 2, feedLines = 40)
        val cmds = parts.map { it[2].toInt() and 0xFF }
        assertEquals(listOf(0xA3, 0xA4, 0xA6, 0xAF, 0xBE, 0xA2, 0xA2, 0xA2, 0xBD, 0xA1, 0xA6, 0xA3), cmds)
        assertArrayEquals(CatPrinterEncoder.energy(0xFFFF), parts[3])
        assertArrayEquals(CatPrinterEncoder.feed(40), parts[9])
    }

    @Test fun `une image plus large est ramenee a 384 points`() {
        val img = MonoImage(576, 1)
        img[96, 0] = true // centre : 576-384 = 192, decalage de -96
        val p = CatPrinterEncoder.encode(img, feedLines = 0).first { (it[2].toInt() and 0xFF) == 0xA2 }
        assertEquals(0x01.toByte(), p[6])
    }

    @Test fun `controle de flux`() {
        assertEquals(true, CatPrinterEncoder.parseFlowControl(CatPrinterEncoder.packet(0xAE, ints(0x10))))
        assertEquals(false, CatPrinterEncoder.parseFlowControl(CatPrinterEncoder.packet(0xAE, ints(0x00))))
        assertNull(CatPrinterEncoder.parseFlowControl(CatPrinterEncoder.getState()))
        assertNull(CatPrinterEncoder.parseFlowControl(ints(1, 2)))
    }
}
