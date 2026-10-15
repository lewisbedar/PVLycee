package fr.pvlycee.ticket

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import fr.pvlycee.domain.DefaultCatalog
import fr.pvlycee.domain.PvFactory
import fr.pvlycee.domain.PvRequest
import fr.pvlycee.domain.Student
import fr.pvlycee.print.Bitmaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendu réel du ticket (moteur graphique natif de Robolectric). Les PNG sont écrits dans build/captures. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TicketRendererTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val renderer = TicketRenderer(context)

    private fun draft(student: Student?, previous: Int, article: String = "M-2") = PvFactory.build(
        PvRequest(
            student = student, className = "2nde 3",
            category = DefaultCatalog.categories.first { c -> c.id == DefaultCatalog.infractions.first { it.article == article }.categoryId },
            infraction = DefaultCatalog.infractions.first { it.article == article },
            gage = "Ramasser 3 papiers dans la salle", previousPvCount = previous,
            school = "Lycée Victor Hugo", room = "B12", agent = "M. Martin",
            lastPvYear = 2026, lastPvSeq = 6, now = 1792000000000L,
        ),
    )

    private fun assertPureBlackAndWhite(b: Bitmap) {
        val row = IntArray(b.width)
        var black = 0
        for (y in 0 until b.height) {
            b.getPixels(row, 0, b.width, 0, y, b.width, 1)
            for (c in row) {
                assertTrue("niveau de gris trouvé : ${Integer.toHexString(c)}", c == Color.BLACK || c == Color.WHITE)
                if (c == Color.BLACK) black++
            }
        }
        assertTrue("ticket vide", black > 1000)
    }

    private fun save(b: Bitmap, name: String) {
        val dir = File("build/captures").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `ticket 58 mm en noir et blanc pur`() {
        val b = renderer.render(draft(Student(1, "Léa", "2nde 3", 12), previous = 1).ticket, 384)
        assertEquals(384, b.width)
        assertTrue(b.height in 800..3000)
        assertPureBlackAndWhite(b)
        assertEquals(384, Bitmaps.toMono(b).width)
        save(b, "ticket-58mm")
    }

    @Test fun `ticket 80 mm, permis suspendu`() {
        val b = renderer.render(draft(Student(2, "Maxime-Alexandre", "2nde 3", 2), previous = 6).ticket, 576)
        assertEquals(576, b.width)
        assertPureBlackAndWhite(b)
        save(b, "ticket-80mm-suspendu")
    }

    @Test fun `ticket contrevenant non identifie`() {
        val b = renderer.render(draft(null, previous = 0, article = "P-1").ticket, 384)
        assertPureBlackAndWhite(b)
        save(b, "ticket-non-identifie")
    }

    @Test fun `mire de test`() {
        val b = renderer.renderTestPattern(384, "Imprimante de test")
        assertPureBlackAndWhite(b)
        save(b, "mire-58mm")
    }
}
