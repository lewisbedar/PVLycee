package fr.pvlycee.raccourcis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast

/**
 * Icône qui ouvre une application du système dont le lanceur Sunmi masque l'icône,
 * puis se ferme aussitôt (aucun écran propre).
 */
abstract class ShortcutActivity : Activity() {
    abstract val labelRes: Int
    abstract val targetPackage: String

    /** Écrans connus de l'application cible, essayés en premier. */
    open val knownActivities: List<String> = emptyList()

    /** Intentions de secours si l'application cible n'a pas l'écran attendu. */
    open fun fallbackIntents(): List<Intent> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = resolve()
        if (intent == null) {
            Toast.makeText(this, getString(R.string.not_found, getString(labelRes)), Toast.LENGTH_LONG).show()
        } else {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.not_found, getString(labelRes)), Toast.LENGTH_LONG).show()
            } catch (_: SecurityException) {
                Toast.makeText(this, getString(R.string.not_found, getString(labelRes)), Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    /** Ordre : écran connu, entrée de lanceur, intention générique, premier écran ouvert. */
    private fun resolve(): Intent? {
        val pm = packageManager
        for (cls in knownActivities) {
            val cn = ComponentName(targetPackage, cls)
            val info = try { pm.getActivityInfo(cn, 0) } catch (_: PackageManager.NameNotFoundException) { null }
            if (info != null && info.exported && info.isEnabled) return Intent(Intent.ACTION_MAIN).setComponent(cn)
        }
        pm.getLaunchIntentForPackage(targetPackage)?.let { return it }
        for (i in fallbackIntents()) {
            val target = pm.resolveActivity(i, 0)?.activityInfo ?: continue
            if (target.packageName != packageName) return i
        }
        val activities = try {
            pm.getPackageInfo(targetPackage, PackageManager.GET_ACTIVITIES).activities
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val first = activities?.firstOrNull { it.exported && it.isEnabled } ?: return null
        return Intent(Intent.ACTION_MAIN).setComponent(ComponentName(targetPackage, first.name))
    }
}

class CameraShortcut : ShortcutActivity() {
    override val labelRes = R.string.camera
    override val targetPackage = "com.mediatek.camera"
    override val knownActivities = listOf("com.mediatek.camera.CameraActivity")
    override fun fallbackIntents() = listOf(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
}

class CalculatorShortcut : ShortcutActivity() {
    override val labelRes = R.string.calculator
    override val targetPackage = "com.google.android.apps.calculator"
    override val knownActivities = listOf("com.android.calculator2.Calculator")
    override fun fallbackIntents() = listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
}

class GalleryShortcut : ShortcutActivity() {
    override val labelRes = R.string.gallery
    override val targetPackage = "com.sunmi.gallery"
    override fun fallbackIntents() = listOf(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY).setPackage(targetPackage),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY),
    )
}
