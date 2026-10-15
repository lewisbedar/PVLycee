package fr.pvlycee.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fr.pvlycee.MainActivity
import fr.pvlycee.PvApp
import fr.pvlycee.domain.CsvStudent
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Parcours « 3 touches » et captures d'écran sur l'écran du Sunmi V2 (720 × 1440, xhdpi)
 * et sur un petit écran ancien (480 × 800, hdpi). Les PNG sont écrits dans build/captures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val prefix: String) {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun seed() {
        val app = ApplicationProvider.getApplicationContext<PvApp>()
        runBlocking {
            app.repository.ensureSeeded()
            app.repository.importStudents(
                listOf("Léa", "Yanis", "Inès", "Noah", "Chloé", "Mathéo", "Jade", "Lucas")
                    .map { CsvStudent(it, "2nde 3") } + CsvStudent("Tom", "1re 2"),
            )
            app.settings.update { it.copy(schoolName = "Lycée Victor Hugo", defaultRoom = "B12", agentName = "M. Martin", lastClass = "2nde 3") }
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val b = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/captures").apply { mkdirs() }
        File(dir, "$prefix-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun parcours() {
        shot("1-verbaliser")
        rule.onNodeWithText("2nde 3").performClick()
        // Touche 1 : l'élève ; touche 2 : l'infraction ; touche 3 : VERBALISER.
        rule.onNodeWithText("Léa").performClick()
        rule.onAllNodesWithText("Pieds sur la table")[0].performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        shot("2-selection")
        rule.onNodeWithText("Enregistrer sans imprimer").assertIsEnabled()
        rule.onNodeWithText("APERÇU DU TICKET").performScrollTo()
        shot("3-apercu")
        rule.onNodeWithText("Enregistrer sans imprimer").performClick()
        rule.waitForIdle()
        shot("4-enregistre")
        // Onglet de catégorie puis tuile.
        rule.onNodeWithText("Contrevenant non identifié").performScrollTo().performClick()
        rule.onNodeWithText("Avachissement en position allongée").performScrollTo()
        rule.onNodeWithText("Mastication").performScrollTo().performClick()
        rule.onNodeWithText("Chewing-gum collé sous la table").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(500)
        shot("4b-mastication")
        rule.onNodeWithText("Registre").performClick()
        shot("5-registre")
        rule.onNodeWithText("Réglages").performClick()
        shot("6-reglages")
        rule.onNodeWithText("Choisir").performClick()
        shot("7-imprimante")
    }
}

@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class SunmiScreenshotTest : ScreenshotBase("sunmi-720x1440")

// Capture impossible sous Robolectric avant l'API 26 ; la mise en page ne dépend que de la taille d'écran.
@Config(sdk = [26], qualifiers = "w320dp-h533dp-hdpi")
class SmallScreenshotTest : ScreenshotBase("petit-480x800")
