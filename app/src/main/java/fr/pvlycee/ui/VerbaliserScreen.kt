package fr.pvlycee.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pvlycee.domain.Infraction
import fr.pvlycee.domain.PvFactory
import fr.pvlycee.print.PrinterKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerbaliserScreen(
    vm: VerbaliserViewModel,
    onOpenPrinter: () -> Unit,
    onOpenRegistre: () -> Unit,
) {
    val sel by vm.selection.collectAsStateWithLifecycle()
    val students by vm.students.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val status by vm.printers.status.collectAsStateWithLifecycle()
    vm.pvs.collectAsStateWithLifecycle()
    var showPreview by rememberSaveable { mutableStateOf(true) }

    val classes = vm.classes(students)
    val className = sel.className.takeIf { it in classes } ?: classes.firstOrNull().orEmpty()
    val inClass = students.filter { it.className == className }
    val enabledCats = catalog.categories.filter { it.enabled }
    val currentCat = sel.categoryId?.takeIf { id -> enabledCats.any { it.id == id } } ?: enabledCats.firstOrNull()?.id
    val selectedInf = catalog.infractions.firstOrNull { it.id == sel.infractionId }
    val isPreviewMode = status.printer.kind == PrinterKind.PREVIEW

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        ) {
            // 1. Classe
            SectionHeader("Classe")
            if (classes.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Aucun élève enregistré. Ajoutez vos classes dans le Registre (saisie ou import CSV).")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onOpenRegistre, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ouvrir le Registre") }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(classes) { c ->
                        FilterChip(
                            selected = c == className,
                            onClick = { vm.chooseClass(c) },
                            label = { Text(c, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp)) },
                            colors = navyChip(),
                        )
                    }
                }
            }

            // 2. Contrevenant
            SectionHeader("Contrevenant")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                inClass.forEach { s ->
                    BigToggle(
                        selected = sel.studentId == s.id,
                        onClick = { vm.chooseStudent(s.id) },
                        title = s.firstName,
                        subtitle = if (s.points <= 0) "suspendu" else "${s.points} pts",
                    )
                }
                BigToggle(
                    selected = sel.studentId == Selection.UNKNOWN,
                    onClick = { vm.chooseStudent(Selection.UNKNOWN) },
                    title = PvFactory.UNKNOWN_OFFENDER,
                    subtitle = null,
                    dashed = true,
                )
            }

            // 3. Infraction
            SectionHeader("Infraction")
            val frequent = vm.frequent()
            if (frequent.isNotEmpty()) {
                Text("Fréquentes", style = MaterialTheme.typography.labelLarge, color = PvColors.NavyLight)
                Spacer(Modifier.height(4.dp))
                TileGrid(frequent, sel.infractionId, highlight = true) { vm.chooseInfraction(it) }
                Spacer(Modifier.height(10.dp))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(enabledCats) { c ->
                    FilterChip(
                        selected = c.id == currentCat,
                        onClick = { vm.chooseCategory(c.id) },
                        label = { Text(c.name, modifier = Modifier.padding(vertical = 8.dp)) },
                        colors = navyChip(),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TileGrid(
                catalog.infractions.filter { it.enabled && it.categoryId == currentCat },
                sel.infractionId,
                highlight = false,
            ) { vm.chooseInfraction(it) }
            if (selectedInf?.freeText == true) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sel.freeText,
                    onValueChange = vm::setFreeText,
                    label = { Text("Nature de l'infraction") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 4. Gage
            if (selectedInf != null) {
                SectionHeader("Peine encourue")
                OutlinedTextField(
                    value = sel.gage,
                    onValueChange = vm::setGage,
                    label = { Text(if (sel.gageEdited) "Gage (modifié)" else "Gage proposé") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(vm.gageChoices(selectedInf.categoryId)) { g ->
                        FilterChip(
                            selected = !g.custom && sel.gage == g.label,
                            onClick = { vm.chooseGage(g) },
                            label = { Text(if (g.custom) "Texte libre" else g.label) },
                        )
                    }
                    if (sel.gageEdited) item {
                        TextButton(onClick = vm::resetGage) { Text("Gage automatique") }
                    }
                }
            }

            // 5. Aperçu
            SectionHeader("Aperçu du ticket")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.label(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { showPreview = !showPreview }) { Text(if (showPreview) "Masquer" else "Afficher") }
            }
            if (showPreview) {
                val bmp = preview
                if (bmp == null) {
                    Text(
                        "Choisissez un contrevenant et une infraction pour voir le ticket.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Surface(shadowElevation = 3.dp, color = Color.White, modifier = Modifier.widthIn(max = 340.dp)) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Aperçu du ticket",
                                contentScale = ContentScale.FillWidth,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.fillMaxWidth().padding(6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Barre d'action fixe en bas.
        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val failed = phase as? PrintPhase.Failed
                if (failed != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Impression impossible", fontWeight = FontWeight.Bold, color = PvColors.Danger)
                            Text(failed.message, style = MaterialTheme.typography.bodyMedium)
                            Text("Le PV n'a pas été enregistré.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = vm::cancelFailed, modifier = Modifier.weight(1f).height(48.dp)) { Text("Annuler") }
                                OutlinedButton(onClick = onOpenPrinter, modifier = Modifier.weight(1f).height(48.dp)) { Text("Imprimante") }
                                Button(onClick = { vm.verbaliser(retry = true) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Réessayer") }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                done?.let {
                    Text(it, color = PvColors.Ok, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                }
                val printing = phase is PrintPhase.Printing
                if (isPreviewMode) {
                    Button(
                        onClick = vm::saveWithoutPrinting,
                        enabled = vm.canSubmit(),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Enregistrer sans imprimer", fontSize = 18.sp) }
                } else {
                    Button(
                        onClick = { vm.verbaliser() },
                        enabled = vm.canSubmit() && failed == null,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PvColors.Navy, contentColor = PvColors.Yellow),
                    ) {
                        if (printing) {
                            CircularProgressIndicator(color = PvColors.Yellow, strokeWidth = 3.dp, modifier = Modifier.height(24.dp))
                            Text("  IMPRESSION…", fontFamily = Oswald, fontSize = 22.sp)
                        } else {
                            Text("VERBALISER", fontFamily = Oswald, fontSize = 26.sp, letterSpacing = 2.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun navyChip() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PvColors.Navy,
    selectedLabelColor = Color.White,
)

/** Bouton « élève », grande cible tactile. */
@Composable
private fun BigToggle(selected: Boolean, onClick: () -> Unit, title: String, subtitle: String?, dashed: Boolean = false) {
    val bg = if (selected) PvColors.Navy else Color.White
    val fg = if (selected) Color.White else PvColors.Ink
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(if (dashed) 1.dp else 1.5.dp, if (selected) PvColors.Navy else Color(0xFFC4C7CE)),
        modifier = Modifier.heightIn(min = 52.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = if (selected) PvColors.Yellow else PvColors.NavyLight, fontSize = 12.sp)
        }
    }
}

/** Tuiles d'infractions sur deux colonnes. */
@Composable
private fun TileGrid(list: List<Infraction>, selectedId: Long?, highlight: Boolean, onPick: (Infraction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { inf ->
                    val sel = inf.id == selectedId
                    Surface(
                        onClick = { onPick(inf) },
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            sel -> PvColors.Navy
                            highlight -> PvColors.YellowPale
                            else -> Color.White
                        },
                        border = BorderStroke(1.5.dp, if (sel) PvColors.Navy else if (highlight) PvColors.Yellow else Color(0xFFC4C7CE)),
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.Center) {
                            Text(
                                inf.label + if (inf.freeText) " (texte libre)" else "",
                                color = if (sel) Color.White else PvColors.Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                lineHeight = 17.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Art. ${inf.article} · ${inf.points} pt${if (inf.points > 1) "s" else ""}",
                                color = if (sel) PvColors.Yellow else PvColors.NavyLight,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
