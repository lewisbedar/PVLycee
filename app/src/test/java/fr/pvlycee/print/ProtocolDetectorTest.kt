package fr.pvlycee.print

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolDetectorTest {

    @Test fun `noms de cat printers`() {
        listOf("GB01", "GB02", "GT01", "MX05", "MX10", "YT01", "MXW01").forEach {
            assertTrue(it, ProtocolDetector.looksLikeCatPrinter(it))
        }
        listOf("MTP-II", "RPP02N", "Printer001", null).forEach { assertFalse(ProtocolDetector.looksLikeCatPrinter(it)) }
    }

    @Test fun `service ae30, cat printer`() {
        val p = ProtocolDetector.detect(PrinterProtocol.AUTO, BtTransport.LE, "XYZ", listOf("0000ae30-0000-1000-8000-00805f9b34fb"))
        assertEquals(PrinterProtocol.CAT, p)
    }

    @Test fun `services ESC-POS BLE courants`() {
        listOf("000018f0-0000-1000-8000-00805f9b34fb", "0000ff00-0000-1000-8000-00805f9b34fb",
            "49535343-FE7D-4AE5-8FA9-9FAFD205E455", "e7810a71-73ae-499d-8c15-faa9aef0c3f2").forEach {
            assertEquals(PrinterProtocol.ESCPOS_BLE, ProtocolDetector.detect(PrinterProtocol.AUTO, BtTransport.LE, "P", listOf(it)))
        }
    }

    @Test fun `bluetooth classique par defaut et protocole force prioritaire`() {
        assertEquals(PrinterProtocol.ESCPOS_CLASSIC, ProtocolDetector.detect(PrinterProtocol.AUTO, BtTransport.CLASSIC, "MTP-II", null))
        assertEquals(PrinterProtocol.CAT, ProtocolDetector.detect(PrinterProtocol.AUTO, BtTransport.UNKNOWN, "GB03", null))
        assertEquals(PrinterProtocol.ESCPOS_BLE, ProtocolDetector.detect(PrinterProtocol.AUTO, BtTransport.LE, "Printer", null))
        assertEquals(
            PrinterProtocol.ESCPOS_BLE,
            ProtocolDetector.detect(PrinterProtocol.ESCPOS_BLE, BtTransport.CLASSIC, "GB01", listOf("ae30")),
        )
    }

    @Test fun `forme courte des UUID`() {
        assertEquals("18f0", ProtocolDetector.shortForm("000018F0-0000-1000-8000-00805F9B34FB"))
        assertTrue(ProtocolDetector.matches("0000fff0-0000-1000-8000-00805f9b34fb", "fff0"))
        assertTrue(ProtocolDetector.isStandardService("00001800-0000-1000-8000-00805f9b34fb"))
        assertTrue(ProtocolDetector.rankService("0000ff00-0000-1000-8000-00805f9b34fb") < ProtocolDetector.rankService("0000abcd-0000-1000-8000-00805f9b34fb"))
    }
}
