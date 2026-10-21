package fr.pvlycee.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pvlycee.photo.PhotoProcessor
import fr.pvlycee.print.PrinterKind
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun PhotoScreen(vm: PhotoViewModel, onOpenPrinter: () -> Unit) {
    val source by vm.source.collectAsStateWithLifecycle()
    if (source == null) CaptureStep(vm) else ReviewStep(vm, onOpenPrinter)
}

/** Étape 1 : viseur intégré (pas besoin d'application Appareil photo) ou choix d'une image. */
@Composable
private fun CaptureStep(vm: PhotoViewModel) {
    val context = LocalContext.current
    val message by vm.message.collectAsStateWithLifecycle()
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var permTick by remember { mutableIntStateOf(0) }
    val granted = remember(permTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::loadFrom) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()
    }
    var capturing by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasCamera -> Text(
                    "Cet appareil n'a pas d'appareil photo. Vous pouvez choisir une image enregistrée.",
                    color = Color.White, modifier = Modifier.padding(24.dp),
                )
                !granted -> Card(Modifier.padding(16.dp), colors = CardDefaults.cardColors(containerColor = PvColors.NavyPale)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PVLycée a besoin de l'appareil photo pour photographier ce que vous voulez imprimer. Les photos ne sont jamais enregistrées.")
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Autoriser l'appareil photo")
                        }
                    }
                }
                else -> CameraViewfinder(
                    imageCapture = imageCapture,
                    onReady = { cameraReady = true },
                    onError = { cameraReady = false; vm.reportError(it) },
                )
            }
            if (capturing) CircularProgressIndicator(color = PvColors.Yellow)
        }
        Surface(shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                message?.let { Text(it, color = PvColors.Danger, modifier = Modifier.padding(bottom = 6.dp)) }
                if (hasCamera && granted) {
                    Button(
                        onClick = {
                            capturing = true
                            takePhoto(context, imageCapture,
                                onDone = { capturing = false; vm.setSource(it) },
                                onError = { capturing = false; vm.reportError(it) },
                            )
                        },
                        enabled = cameraReady && !capturing,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PvColors.Navy, contentColor = PvColors.Yellow),
                    ) { Text("PHOTOGRAPHIER", fontFamily = Oswald, fontSize = 24.sp, letterSpacing = 2.sp) }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = { pickLauncher.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Choisir une image enregistrée") }
            }
        }
    }
}

/** Aperçu CameraX, lié au cycle de vie de l'écran : la caméra s'éteint quand on le quitte. */
@Composable
private fun CameraViewfinder(imageCapture: ImageCapture, onReady: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView : plus fiable sur les anciens terminaux.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    DisposableEffect(lifecycleOwner) {
        var provider: ProcessCameraProvider? = null
        val future = try { ProcessCameraProvider.getInstance(context) } catch (e: Exception) {
            onError("Appareil photo indisponible : ${e.message ?: "erreur"}"); null
        }
        future?.addListener({
            try {
                val p = future.get()
                provider = p
                val selector = when {
                    p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> { onError("Aucun appareil photo utilisable."); return@addListener }
                }
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                onReady()
            } catch (e: Exception) {
                onError("Appareil photo indisponible : ${e.message ?: "erreur"}")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            try { provider?.unbindAll() } catch (_: Exception) {}
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun takePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onDone: (Bitmap) -> Unit,
    onError: (String) -> Unit,
) {
    val worker = Executors.newSingleThreadExecutor()
    val main = ContextCompat.getMainExecutor(context)
    try {
        imageCapture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val result = try {
                    val rotation = image.imageInfo.rotationDegrees
                    PhotoProcessor.rotate(PhotoProcessor.downscale(image.toBitmap()), rotation)
                } catch (e: Exception) {
                    null
                } finally {
                    image.close()
                }
                main.execute { if (result != null) onDone(result) else onError("Photo illisible, réessayez.") }
                worker.shutdown()
            }

            override fun onError(exception: ImageCaptureException) {
                main.execute { onError("Prise de vue impossible : ${exception.message ?: "erreur"}") }
                worker.shutdown()
            }
        })
    } catch (e: Exception) {
        worker.shutdown()
        onError("Prise de vue impossible : ${e.message ?: "erreur"}")
    }
}

/** Étape 2 : réglages, aperçu du ticket (le même Bitmap que celui imprimé) et impression. */
@Composable
private fun ReviewStep(vm: PhotoViewModel, onOpenPrinter: () -> Unit) {
    val ticket by vm.ticket.collectAsStateWithLifecycle()
    val opts by vm.options.collectAsStateWithLifecycle()
    val caption by vm.caption.collectAsStateWithLifecycle()
    val evidence by vm.evidence.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val status by vm.printers.status.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val bmp = ticket
                if (bmp == null) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                } else {
                    Surface(shadowElevation = 3.dp, color = Color.White, modifier = Modifier.widthIn(max = 320.dp)) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Aperçu du ticket photo",
                            contentScale = ContentScale.FillWidth,
                            // Lissé : à l'écran, une trame affichée point par point crée des moirés.
                            filterQuality = FilterQuality.Low,
                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::discard, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Reprendre") }
                OutlinedButton(onClick = vm::rotate, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Pivoter") }
            }

            SectionHeader("Rendu")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = opts.dither, onClick = { vm.setDither(true) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Photo (tramé)") }
                SegmentedButton(
                    selected = !opts.dither, onClick = { vm.setDither(false) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Texte, dessin") }
            }
            Spacer(Modifier.height(6.dp))
            Text("Luminosité : ${if (opts.brightness > 0) "+" else ""}${opts.brightness}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = opts.brightness.toFloat(),
                onValueChange = { vm.setBrightness(it.roundToInt()) },
                valueRange = -60f..80f,
            )
            Text("Contraste : ${(opts.contrast * 100).roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = opts.contrast,
                onValueChange = { vm.setContrast((it * 20).roundToInt() / 20f) },
                valueRange = 0.6f..2.0f,
            )

            SectionHeader("Présentation")
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bandeau « Pièce à conviction »", modifier = Modifier.weight(1f))
                Switch(checked = evidence, onCheckedChange = vm::setEvidence)
            }
            OutlinedTextField(
                value = caption,
                onValueChange = vm::setCaption,
                label = { Text("Légende (facultative)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "La photo n'est pas enregistrée : elle disparaît quand vous quittez l'écran ou reprenez une photo.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        Surface(shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val failed = phase as? PrintPhase.Failed
                if (failed != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Impression impossible", fontWeight = FontWeight.Bold, color = PvColors.Danger)
                            Text(failed.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onOpenPrinter, modifier = Modifier.weight(1f).height(48.dp)) { Text("Imprimante") }
                                Button(onClick = vm::print, modifier = Modifier.weight(1f).height(48.dp)) { Text("Réessayer") }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                message?.let { Text(it, color = PvColors.Ok, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp)) }
                if (status.printer.kind == PrinterKind.PREVIEW && message == null) {
                    Text("Mode aperçu : aucune imprimante choisie.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                }
                val printing = phase is PrintPhase.Printing
                Button(
                    onClick = vm::print,
                    enabled = ticket != null && !printing && failed == null,
                    modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PvColors.Navy, contentColor = PvColors.Yellow),
                ) {
                    Text(
                        if (printing) "IMPRESSION…" else "IMPRIMER",
                        fontFamily = Oswald, fontSize = 22.sp, letterSpacing = 1.sp, maxLines = 1,
                    )
                }
            }
        }
    }
}
