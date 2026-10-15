package fr.pvlycee.print

import java.io.ByteArrayOutputStream

/**
 * Protocole des « cat printers » BLE (GB01, GT01, MX05, MX10…).
 * Paquet : 0x51 0x78 cmd 0x00 len 0x00 data… crc8 0xFF (CRC-8, polynôme 0x07, sur les données).
 * Largeur fixe de 384 points, bits de poids faible en premier.
 */
object CatPrinterEncoder {
    const val WIDTH = 384
    const val BYTES_PER_ROW = WIDTH / 8

    const val CMD_RETRACT = 0xA0
    const val CMD_FEED = 0xA1
    const val CMD_DRAW_LINE = 0xA2
    const val CMD_GET_STATE = 0xA3
    const val CMD_QUALITY = 0xA4
    const val CMD_LATTICE = 0xA6
    const val CMD_FLOW = 0xAE
    const val CMD_ENERGY = 0xAF
    const val CMD_SPEED = 0xBD
    const val CMD_DRAWING_MODE = 0xBE

    private val LATTICE_START = intArrayOf(0xAA, 0x55, 0x17, 0x38, 0x44, 0x5F, 0x5F, 0x5F, 0x44, 0x38, 0x2C)
    private val LATTICE_END = intArrayOf(0xAA, 0x55, 0x17, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x17)

    /** Énergie de chauffe selon l'intensité choisie (0 clair, 1 normal, 2 foncé). */
    fun energyFor(intensity: Int): Int = when (intensity) {
        0 -> 0x2EE0
        2 -> 0xFFFF
        else -> 0x5DC0
    }

    fun crc8(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    fun packet(cmd: Int, data: ByteArray): ByteArray {
        val out = ByteArray(8 + data.size)
        out[0] = 0x51; out[1] = 0x78
        out[2] = cmd.toByte(); out[3] = 0x00
        out[4] = (data.size and 0xFF).toByte(); out[5] = ((data.size shr 8) and 0xFF).toByte()
        data.copyInto(out, 6)
        out[6 + data.size] = crc8(data).toByte()
        out[7 + data.size] = 0xFF.toByte()
        return out
    }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    fun getState() = packet(CMD_GET_STATE, bytes(0x00))
    fun quality200Dpi() = packet(CMD_QUALITY, bytes(0x32))
    fun latticeStart() = packet(CMD_LATTICE, bytes(*LATTICE_START))
    fun latticeEnd() = packet(CMD_LATTICE, bytes(*LATTICE_END))
    fun energy(value: Int) = packet(CMD_ENERGY, bytes(value and 0xFF, (value shr 8) and 0xFF))
    fun applyEnergy() = packet(CMD_DRAWING_MODE, bytes(0x01))
    fun speed(value: Int) = packet(CMD_SPEED, bytes(value and 0xFF))
    fun feed(lines: Int) = packet(CMD_FEED, bytes(lines and 0xFF, (lines shr 8) and 0xFF))

    /** Ligne de 384 points, bits de poids faible en premier. */
    fun drawLine(img: MonoImage, y: Int): ByteArray = packet(CMD_DRAW_LINE, img.rowLsbFirst(y, BYTES_PER_ROW))

    /**
     * Séquence complète : état, qualité 200 dpi, début de treillis, énergie, lignes 0xA2,
     * vitesse et avance papier, fin de treillis, état.
     */
    fun encode(img: MonoImage, intensity: Int = 1, feedLines: Int = 80): List<ByteArray> {
        val src = if (img.width == WIDTH) img else img.fitWidth(WIDTH)
        val parts = ArrayList<ByteArray>()
        parts += getState()
        parts += quality200Dpi()
        parts += latticeStart()
        parts += energy(energyFor(intensity))
        parts += applyEnergy()
        for (y in 0 until src.height) parts += drawLine(src, y)
        parts += speed(0x19)
        if (feedLines > 0) parts += feed(feedLines)
        parts += latticeEnd()
        parts += getState()
        return parts
    }

    fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream(parts.sumOf { it.size })
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    /**
     * Interprète une notification de contrôle de flux (0xAE) : true = pause demandée,
     * false = reprise, null = autre message.
     */
    fun parseFlowControl(packet: ByteArray): Boolean? {
        if (packet.size < 7) return null
        if ((packet[0].toInt() and 0xFF) != 0x51 || (packet[1].toInt() and 0xFF) != 0x78) return null
        if ((packet[2].toInt() and 0xFF) != CMD_FLOW) return null
        return (packet[6].toInt() and 0xFF) != 0x00
    }
}
