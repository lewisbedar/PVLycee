package fr.pvlycee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pvlycee.domain.PvRules
import fr.pvlycee.print.PrinterKind
import fr.pvlycee.print.PrinterState
import fr.pvlycee.print.PrinterStatus

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = SectionTitle, modifier = modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** Barre de 12 cases représentant le permis de bonne conduite. */
@Composable
fun PointsBar(points: Int, modifier: Modifier = Modifier, cell: Int = 10) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until PvRules.MAX_POINTS) {
            val filled = i < points
            Box(
                Modifier.size(cell.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (filled) PvColors.Navy else Color.Transparent)
                    .border(1.dp, if (filled) PvColors.Navy else Color(0xFFB0B4BC), RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * Confirmation intégrée à l'écran pour les actions destructrices (pas de boîte de dialogue).
 */
@Composable
fun InlineConfirm(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(message, color = PvColors.Danger, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) { Text("Annuler") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PvColors.Danger),
                ) { Text(confirmLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Libellé court de l'état de l'imprimante. */
fun PrinterStatus.label(): String = when (val s = state) {
    PrinterState.Disconnected -> "${printer.displayName} : déconnectée"
    PrinterState.Connecting -> "${printer.displayName} : connexion…"
    PrinterState.Ready -> if (printer.kind == PrinterKind.PREVIEW) "Aucune imprimante : le ticket s'affiche seulement à l'écran" else "${printer.displayName} : prête"
    PrinterState.Printing -> "${printer.displayName} : impression…"
    is PrinterState.Error -> "${printer.displayName} : ${s.message}"
}

fun PrinterStatus.color(): Color = when (state) {
    PrinterState.Ready -> if (printer.kind == PrinterKind.PREVIEW) Color(0xFF9AA0A6) else PvColors.Ok
    PrinterState.Printing, PrinterState.Connecting -> PvColors.Yellow
    PrinterState.Disconnected -> Color(0xFF9AA0A6)
    is PrinterState.Error -> PvColors.Danger
}

/** Indicateur discret de l'imprimante, cliquable. */
@Composable
fun PrinterIndicator(status: PrinterStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(status.color()))
        Spacer(Modifier.width(6.dp))
        Text(
            when (status.state) {
                PrinterState.Ready -> if (status.printer.kind == PrinterKind.PREVIEW) "Aperçu" else "Prête"
                PrinterState.Connecting -> "Connexion"
                PrinterState.Printing -> "Impression"
                PrinterState.Disconnected -> "Déconnectée"
                is PrinterState.Error -> "Erreur"
            },
            color = Color.White, fontSize = 13.sp, maxLines = 1,
        )
    }
}
