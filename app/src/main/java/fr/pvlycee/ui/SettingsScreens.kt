package fr.pvlycee.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pvlycee.data.PrinterTransport
import fr.pvlycee.domain.Category
import fr.pvlycee.domain.Gage
import fr.pvlycee.domain.Infraction
import fr.pvlycee.print.PrinterKind
import fr.pvlycee.print.PrinterProtocol

@Composable
private fun MessageCard(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = PvColors.YellowPale),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable(onClick = onDismiss),
    ) { Text(message, modifier = Modifier.padding(12.dp)) }
}

/** Champ texte enregistré à chaque frappe, sans perdre le curseur pendant la saisie. */
@Composable
private fun SettingField(label: String, value: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!editing && value != text) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).onFocusChanged { editing = it.isFocused },
    )
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onOpenPrinter: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenGages: () -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val status by vm.printers.status.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val printer = status.printer

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        MessageCard(message, vm::dismissMessage)

        SectionHeader("Établissement")
        SettingField("Nom de l'établissement", s.schoolName) { v -> vm.update { it.copy(schoolName = v) } }
        SettingField("Salle par défaut", s.defaultRoom) { v -> vm.update { it.copy(defaultRoom = v) } }
        SettingField("Agent verbalisateur", s.agentName) { v -> vm.update { it.copy(agentName = v) } }

        SectionHeader("Imprimante")
        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenPrinter), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(printer.displayName, fontWeight = FontWeight.Bold)
                    Text(status.label(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("Choisir", color = PvColors.Navy, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = vm::printTest, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Imprimer un ticket de test")
        }

        SectionHeader("Papier")
        val fixed = printer.fixedWidthDots != null
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(58 to "58 mm (384 pts)", 80 to "80 mm (576 pts)").forEachIndexed { i, (mm, label) ->
                SegmentedButton(
                    selected = if (fixed) mm == 58 else s.paperWidthMm == mm,
                    onClick = { vm.update { it.copy(paperWidthMm = mm) } },
                    enabled = !fixed,
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        if (fixed) Text("Largeur imposée à 58 mm pour ${printer.displayName}.", style = MaterialTheme.typography.bodySmall)

        SectionHeader("Intensité d'impression")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Claire", "Normale", "Foncée").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = s.intensity == i,
                    onClick = { vm.update { it.copy(intensity = i) } },
                    enabled = printer.supportsIntensity,
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }
        if (!printer.supportsIntensity) {
            Text(
                "Réglage disponible pour les cat printers. Pour les autres, l'intensité se règle sur l'imprimante elle-même.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionHeader("Catalogue")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenCatalog, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Infractions") }
            Button(onClick = onOpenGages, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Gages") }
        }

        SectionHeader("Données")
        Text(
            "PVLycée n'a pas accès à Internet : élèves et PV restent sur cet appareil.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        if (confirmReset) {
            InlineConfirm(
                "Tout effacer : élèves, PV, catalogue personnalisé et réglages ? Pensez à exporter une sauvegarde avant.",
                "Tout effacer",
                { vm.resetAll(); confirmReset = false },
                { confirmReset = false },
            )
        } else {
            TextButton(onClick = { confirmReset = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Remise à zéro complète", color = PvColors.Danger)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("PVLycée 1.0 · Document humoristique sans valeur légale.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PrinterScreen(vm: SettingsViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val status by vm.printers.status.collectAsStateWithLifecycle()
    val sunmi by vm.printers.sunmiAvailable.collectAsStateWithLifecycle()
    val bonded by vm.bonded.collectAsStateWithLifecycle()
    val scan by vm.scanResults.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var permTick by remember { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        vm.refreshBonded()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permTick++
        vm.refreshBonded()
    }
    LaunchedEffect(Unit) { vm.refreshBonded() }

    val missingConnect = remember(permTick) { vm.missingPermissions(scan = false) }
    val missingScan = remember(permTick) { vm.missingPermissions(scan = true) }
    val btOn = remember(permTick) { vm.bluetoothEnabled() }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            MessageCard(message, vm::dismissMessage)
            SectionHeader("Imprimante active")
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text(status.printer.displayName, fontWeight = FontWeight.Bold)
                    Text(status.label(), style = MaterialTheme.typography.bodySmall)
                    if (s.printerTransport == PrinterTransport.CLASSIC || s.printerTransport == PrinterTransport.BLE) {
                        Text("Protocole : ${status.printer.kind.label}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::reconnect, enabled = !busy && status.printer.isPhysical, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("Reconnecter")
                        }
                        Button(onClick = { vm.printTest() }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text("Ticket de test")
                        }
                    }
                    if (busy) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Patientez…")
                        }
                    }
                }
            }

            SectionHeader("Choix rapide")
            if (sunmi) {
                ChoiceRow("Imprimante intégrée Sunmi", "Détectée sur ce terminal", status.printer.kind == PrinterKind.SUNMI) { vm.useSunmi() }
            }
            ChoiceRow("Aperçu seulement", "Aucune impression, pour tester sans matériel", status.printer.kind == PrinterKind.PREVIEW) { vm.usePreview() }

            if (!vm.bluetoothAvailable()) {
                SectionHeader("Bluetooth")
                Text("Cet appareil n'a pas de Bluetooth.")
                return@item
            }

            if (missingConnect.isNotEmpty() || missingScan.isNotEmpty()) {
                SectionHeader("Autorisation")
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PvColors.NavyPale)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (Build.VERSION.SDK_INT >= 31)
                                "PVLycée a besoin du Bluetooth pour trouver votre imprimante et lui envoyer les tickets."
                            else
                                "Android exige l'accès à la position pour rechercher des appareils Bluetooth : PVLycée ne l'utilise que pour trouver l'imprimante.",
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { permLauncher.launch((missingConnect + missingScan).distinct().toTypedArray()) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Autoriser") }
                    }
                }
            }

            if (!btOn && missingConnect.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        try { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Activer le Bluetooth") }
            }

            SectionHeader("Protocole")
            PrinterProtocol.entries.forEach { p ->
                ChoiceRow(
                    p.label,
                    if (p == PrinterProtocol.AUTO) "Recommandé" else "Forcer ce protocole",
                    s.forcedProtocol == p,
                ) { vm.forceProtocol(p) }
            }

            SectionHeader("Appareils appairés (Bluetooth classique)")
            if (missingConnect.isEmpty() && bonded.isEmpty()) {
                Text(
                    "Aucun appareil appairé. Appairez l'imprimante dans les paramètres Bluetooth d'Android (code souvent 0000 ou 1234), ou utilisez la recherche BLE ci-dessous.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    try { enableLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) {}
                }) { Text("Ouvrir les paramètres Bluetooth") }
            }
        }
        items(bonded, key = { "b" + it.address }) { item ->
            DeviceRow(item, selected = s.printerAddress == item.address, enabled = !busy) { vm.select(item) }
        }
        item {
            SectionHeader("Recherche BLE (cat printers et imprimantes BLE)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (scanning) vm.stopScan() else vm.startScan() },
                    enabled = missingScan.isEmpty() && btOn,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (scanning) "Arrêter" else "Rechercher") }
                if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            if (vm.locationServicesOff()) {
                Text(
                    "La localisation est désactivée : sur cette version d'Android, la recherche BLE ne trouve rien sans elle.",
                    style = MaterialTheme.typography.bodySmall, color = PvColors.Danger,
                )
            }
            if (!scanning && scan.isEmpty()) {
                Text("Allumez l'imprimante, puis lancez la recherche (12 secondes).", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(scan, key = { "s" + it.address }) { item ->
            DeviceRow(item, selected = s.printerAddress == item.address, enabled = !busy) { vm.select(item) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DeviceRow(item: BtItem, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.address + (item.rssi?.let { " · signal $it dBm" } ?: "") + if (item.looksLikeCat) " · cat printer probable" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider()
}

// --- Catalogue ---

@Composable
fun CatalogScreen(vm: SettingsViewModel) {
    val cat by vm.catalog.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var editInfraction by remember { mutableStateOf<Infraction?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            MessageCard(message, vm::dismissMessage)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { editCategory = Category(0, "", "Code ", (cat.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) { Text("Nouvelle catégorie") }
                OutlinedButton(onClick = { confirm = "restore" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Par défaut") }
            }
            if (confirm == "restore") {
                Spacer(Modifier.height(6.dp))
                InlineConfirm(
                    "Remplacer le catalogue et les gages par ceux d'origine ? Vos modifications seront perdues (les PV déjà dressés ne changent pas).",
                    "Restaurer", { vm.restoreCatalog(); confirm = null }, { confirm = null },
                )
            }
        }
        cat.categories.forEach { c ->
            item(key = "c${c.id}") {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = if (c.enabled) PvColors.NavyPale else Color(0xFFE6E6E6))) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text(c.codeName, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = c.enabled, onCheckedChange = { vm.saveCategory(c.copy(enabled = it)) })
                        }
                        if (confirm == "c${c.id}") {
                            InlineConfirm(
                                "Supprimer la catégorie ${c.name} et toutes ses infractions ?",
                                "Supprimer", { vm.deleteCategory(c); confirm = null }, { confirm = null },
                            )
                        } else {
                            Row {
                                TextButton(onClick = { editCategory = c }) { Text("Modifier") }
                                TextButton(onClick = {
                                    editInfraction = Infraction(0, c.id, "", "", "", 1, sortOrder = (cat.infractions.maxOfOrNull { it.sortOrder } ?: 0) + 1)
                                }) { Text("Ajouter une infraction") }
                                TextButton(onClick = { confirm = "c${c.id}" }) { Text("Supprimer", color = PvColors.Danger) }
                            }
                        }
                    }
                }
            }
            items(cat.infractions.filter { it.categoryId == c.id }, key = { "i${it.id}" }) { inf ->
                Column(Modifier.padding(start = 8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { editInfraction = inf }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(inf.label + if (inf.freeText) " (texte libre)" else "", fontWeight = FontWeight.Bold, color = if (inf.enabled) Color.Unspecified else Color.Gray)
                            Text(
                                "Art. ${inf.article}" + (if (inf.articleNote.isNotBlank()) " (${inf.articleNote})" else "") + " · ${inf.points} pt${if (inf.points > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = inf.enabled, onCheckedChange = { vm.saveInfraction(inf.copy(enabled = it)) })
                    }
                    if (confirm == "i${inf.id}") {
                        InlineConfirm("Supprimer « ${inf.label} » ?", "Supprimer", { vm.deleteInfraction(inf); confirm = null }, { confirm = null })
                    }
                    HorizontalDivider()
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editCategory?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var code by remember(c) { mutableStateOf(c.codeName) }
        AlertDialog(
            onDismissRequest = { editCategory = null },
            title = { Text(if (c.id == 0L) "Nouvelle catégorie" else "Modifier la catégorie") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Nom (ex. Mobilier)") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(code, { code = it }, label = { Text("Faux code (ex. Code de la Chaise)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.saveCategory(c.copy(name = name, codeName = code)); editCategory = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editCategory = null }) { Text("Annuler") } },
        )
    }

    editInfraction?.let { inf ->
        var label by remember(inf) { mutableStateOf(inf.label) }
        var article by remember(inf) { mutableStateOf(inf.article) }
        var note by remember(inf) { mutableStateOf(inf.articleNote) }
        var points by remember(inf) { mutableStateOf(inf.points.toString()) }
        var free by remember(inf) { mutableStateOf(inf.freeText) }
        AlertDialog(
            onDismissRequest = { editInfraction = null },
            title = { Text(if (inf.id == 0L) "Nouvelle infraction" else "Modifier l'infraction") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(label, { label = it }, label = { Text("Intitulé") })
                    OutlinedTextField(article, { article = it }, label = { Text("Article (ex. C-2)") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Précision (ex. dépôt sauvage)") }, singleLine = true)
                    OutlinedTextField(
                        points, { points = it.filter(Char::isDigit).take(2) }, label = { Text("Points retirés") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = free, onCheckedChange = { free = it })
                        Text("Texte libre (intitulé saisi au moment du PV)")
                    }
                    if (inf.id != 0L) {
                        TextButton(onClick = { confirm = "i${inf.id}"; editInfraction = null }) { Text("Supprimer cette infraction", color = PvColors.Danger) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank() && article.isNotBlank(), onClick = {
                    vm.saveInfraction(
                        inf.copy(label = label, article = article, articleNote = note, points = (points.toIntOrNull() ?: 1).coerceIn(0, 12), freeText = free),
                    )
                    editInfraction = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editInfraction = null }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GagesScreen(vm: SettingsViewModel) {
    val cat by vm.catalog.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Gage?>(null) }
    var confirm by remember { mutableStateOf<Long?>(null) }
    val gages = cat.gages.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    val catName = cat.categories.associate { it.id to it.name }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Le gage proposé dépend du nombre de récidives : 1er PV = 1er gage de la catégorie, 2e PV = 2e gage, etc. « Simple avertissement » reste toujours en tête.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { edit = Gage(0, "", sortOrder = (gages.maxOfOrNull { it.sortOrder } ?: 0) + 1) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Nouveau gage") }
        }
        items(gages, key = { it.id }) { g ->
            val first = g.id == gages.firstOrNull()?.id
            Column(Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable(enabled = !first) { edit = g }) {
                        Text(g.label + if (g.custom) " (texte libre)" else "", fontWeight = FontWeight.Bold, color = if (g.enabled) Color.Unspecified else Color.Gray)
                        Text(
                            if (g.allCategories) "Toutes les catégories" else g.categoryIds.mapNotNull { catName[it] }.joinToString(", ").ifBlank { "Aucune catégorie" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!first) {
                        TextButton(onClick = { vm.moveGage(g, up = true) }) { Text("▲") }
                        TextButton(onClick = { vm.moveGage(g, up = false) }) { Text("▼") }
                        Switch(checked = g.enabled, onCheckedChange = { vm.saveGage(g.copy(enabled = it)) })
                    }
                }
                if (confirm == g.id) {
                    InlineConfirm("Supprimer le gage « ${g.label} » ?", "Supprimer", { vm.deleteGage(g); confirm = null }, { confirm = null })
                }
                HorizontalDivider()
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    edit?.let { g ->
        var label by remember(g) { mutableStateOf(g.label) }
        var all by remember(g) { mutableStateOf(g.allCategories) }
        var ids by remember(g) { mutableStateOf(g.categoryIds) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (g.id == 0L) "Nouveau gage" else "Modifier le gage") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(label, { label = it }, label = { Text("Gage") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = all, onCheckedChange = { all = it })
                        Text("Toutes les catégories")
                    }
                    if (!all) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cat.categories.forEach { c ->
                                FilterChip(
                                    selected = c.id in ids,
                                    onClick = { ids = if (c.id in ids) ids - c.id else ids + c.id },
                                    label = { Text(c.name) },
                                )
                            }
                        }
                    }
                    if (g.id != 0L) {
                        TextButton(onClick = { confirm = g.id; edit = null }) { Text("Supprimer ce gage", color = PvColors.Danger) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank(), onClick = {
                    vm.saveGage(g.copy(label = label, allCategories = all, categoryIds = ids)); edit = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Annuler") } },
        )
    }
}
