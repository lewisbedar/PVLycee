package fr.pvlycee.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.pvlycee.domain.Pv
import fr.pvlycee.domain.PvRules
import fr.pvlycee.domain.Stats
import fr.pvlycee.domain.Student
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistreScreen(vm: RegistreViewModel, onOpenStudent: (Long) -> Unit) {
    val students by vm.students.collectAsStateWithLifecycle()
    val pvs by vm.pvs.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val classes = students.map { it.className }.distinct().sortedWith(VerbaliserViewModel.naturalOrder)
    val lastClass by vm.lastClass.collectAsStateWithLifecycle()
    var selectedClass by rememberSaveable { mutableStateOf("") }
    val cls = selectedClass.takeIf { it in classes } ?: lastClass.takeIf { it in classes } ?: classes.firstOrNull().orEmpty()

    var confirm by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importCsv) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportJson) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importJson) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            message?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PvColors.YellowPale),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { vm.dismissMessage() },
                ) { Text(it, modifier = Modifier.padding(12.dp)) }
            }
            SectionHeader("Classes")
            if (classes.isEmpty()) {
                Text("Aucune classe pour l'instant. Ajoutez un élève ou importez une liste CSV (prénom ; classe).")
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(classes) { c ->
                    FilterChip(
                        selected = c == cls,
                        onClick = { selectedClass = c; confirm = null },
                        label = { Text(c, fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PvColors.Navy, selectedLabelColor = Color.White),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { showAdd = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ajouter un élève") }
                OutlinedButton(onClick = { csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Importer un CSV")
                }
            }
        }

        if (cls.isNotBlank()) {
            val recap = Stats.classRecap(cls, pvs)
            item {
                SectionHeader("Récapitulatif : $cls")
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = PvColors.NavyPale)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${recap.totalPv} PV au total", fontWeight = FontWeight.Bold)
                        recap.byCategory.forEach { (cat, n) -> Text("$cat : $n PV") }
                        recap.specialty?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontWeight = FontWeight.Bold, color = PvColors.Navy)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (confirm) {
                    "amnesty" -> InlineConfirm(
                        "Rendre les 12 points à tous les élèves de la $cls ? L'historique des PV est conservé.",
                        "Amnistier", { vm.amnestyClass(cls); confirm = null }, { confirm = null },
                    )
                    "delete" -> InlineConfirm(
                        "Supprimer la classe $cls, tous ses élèves et leurs PV ? Cette action est définitive.",
                        "Supprimer", { vm.deleteClass(cls); confirm = null }, { confirm = null },
                    )
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { confirm = "amnesty" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Amnistie générale") }
                        OutlinedButton(onClick = { showRename = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Renommer") }
                        TextButton(onClick = { confirm = "delete" }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Supprimer la classe", color = PvColors.Danger)
                        }
                    }
                }
                SectionHeader("Élèves")
            }
            val inClass = students.filter { it.className == cls }
            items(inClass, key = { it.id }) { s ->
                val mine = pvs.filter { it.studentId == s.id }
                StudentRow(s, mine.size, mine.maxByOrNull { it.timestamp }) { onOpenStudent(s.id) }
                HorizontalDivider()
            }
        }

        item {
            SectionHeader("Sauvegarde")
            Text(
                "Fichier JSON complet : élèves, PV, catalogue et réglages. Il reste sur l'appareil ou la clé où vous l'enregistrez.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            if (confirm == "restore") {
                InlineConfirm(
                    "Restaurer une sauvegarde remplace toutes les données actuelles. Continuer ?",
                    "Choisir le fichier",
                    { confirm = null; importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    { confirm = null },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("pvlycee-sauvegarde-${SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())}.json") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Exporter") }
                    OutlinedButton(onClick = { confirm = "restore" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Restaurer") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAdd) {
        AddStudentDialog(defaultClass = cls, onDismiss = { showAdd = false }) { name, c ->
            vm.addStudent(name, c); selectedClass = c.trim()
        }
    }
    if (showRename && cls.isNotBlank()) {
        TextInputDialog("Renommer la classe", "Nouveau nom", cls, onDismiss = { showRename = false }) {
            vm.renameClass(cls, it); selectedClass = it.trim()
        }
    }
}

@Composable
private fun StudentRow(s: Student, count: Int, last: Pv?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.firstName, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$count PV" + (last?.let { " · dernier le ${dateFmt.format(Date(it.timestamp))}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            PointsBar(s.points, cell = 9)
            Text(
                if (s.points <= 0) "Permis suspendu" else "${s.points} / ${PvRules.MAX_POINTS} pts",
                style = MaterialTheme.typography.bodySmall,
                color = if (s.points <= 0) PvColors.Danger else PvColors.NavyLight,
            )
        }
    }
}

@Composable
fun StudentDetailScreen(vm: RegistreViewModel, studentId: Long, onDeleted: () -> Unit) {
    val students by vm.students.collectAsStateWithLifecycle()
    val pvs by vm.pvs.collectAsStateWithLifecycle()
    val s = students.firstOrNull { it.id == studentId }
    var confirm by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf(false) }
    if (s == null) {
        Text("Élève introuvable.", modifier = Modifier.padding(16.dp))
        return
    }
    val history = pvs.filter { it.studentId == s.id }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text(s.firstName, style = MaterialTheme.typography.headlineSmall)
            Text("Classe : ${s.className}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("Permis de bonne conduite", style = SectionTitle)
            Spacer(Modifier.height(4.dp))
            PointsBar(s.points, cell = 18)
            Text(
                if (s.points <= 0) "PERMIS SUSPENDU" else "${s.points} / ${PvRules.MAX_POINTS} points",
                fontWeight = FontWeight.Bold,
                color = if (s.points <= 0) PvColors.Danger else PvColors.Navy,
            )
            Spacer(Modifier.height(10.dp))
            when (confirm) {
                "amnesty" -> InlineConfirm("Rendre ses 12 points à ${s.firstName} ?", "Amnistier", { vm.amnesty(s); confirm = null }, { confirm = null })
                "delete" -> InlineConfirm(
                    "Supprimer ${s.firstName} et ses ${history.size} PV ? Cette action est définitive.",
                    "Supprimer", { vm.deleteStudent(s); confirm = null; onDeleted() }, { confirm = null },
                )
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { confirm = "amnesty" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Amnistie") }
                    OutlinedButton(onClick = { rename = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Renommer") }
                    TextButton(onClick = { confirm = "delete" }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text("Supprimer", color = PvColors.Danger)
                    }
                }
            }
            SectionHeader("Historique (${history.size} PV)")
            if (history.isEmpty()) Text("Casier vierge. Bravo !")
        }
        items(history, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(10.dp)) {
                    Row {
                        Text(p.number, fontFamily = Mono, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(dateTimeFmt.format(Date(p.timestamp)), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(p.infractionLabel, fontWeight = FontWeight.Bold)
                    Text("${p.article} · ${PvRules.contraventionClassLabel(p.contraventionClass)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Retrait : ${p.pointsRemoved} pt${if (p.pointsRemoved > 1) "s" else ""}" + (p.pointsAfter?.let { ", solde $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (p.gage.isNotBlank()) Text("Gage : ${p.gage}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (rename) {
        TextInputDialog("Renommer l'élève", "Prénom", s.firstName, onDismiss = { rename = false }) { vm.renameStudent(s, it) }
    }
}

@Composable
fun AddStudentDialog(defaultClass: String, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cls by remember { mutableStateOf(defaultClass) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un élève") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Prénom") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = cls, onValueChange = { cls = it }, label = { Text("Classe (ex. 2nde 3)") }, singleLine = true)
                Text("Pour créer une classe, saisissez simplement son nom.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, cls); name = "" }, enabled = name.isNotBlank() && cls.isNotBlank()) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
fun TextInputDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(value); onDismiss() }, enabled = value.isNotBlank()) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
