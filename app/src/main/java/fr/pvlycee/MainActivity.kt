package fr.pvlycee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.pvlycee.ui.CatalogScreen
import fr.pvlycee.ui.GagesScreen
import fr.pvlycee.ui.HazardStripe
import fr.pvlycee.ui.Oswald
import fr.pvlycee.ui.PhotoScreen
import fr.pvlycee.ui.PhotoViewModel
import fr.pvlycee.ui.PrinterIndicator
import fr.pvlycee.ui.PrinterScreen
import fr.pvlycee.ui.PvColors
import fr.pvlycee.ui.PvTheme
import fr.pvlycee.ui.RegistreScreen
import fr.pvlycee.ui.RegistreViewModel
import fr.pvlycee.ui.SettingsScreen
import fr.pvlycee.ui.SettingsViewModel
import fr.pvlycee.ui.StudentDetailScreen
import fr.pvlycee.ui.VerbaliserScreen
import fr.pvlycee.ui.VerbaliserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PvTheme { PvAppUi() } }
    }
}

/** Pile de navigation minimale : un écran racine par onglet, puis des sous-écrans. */
private object Routes {
    const val VERBALISER = "verbaliser"
    const val REGISTRE = "registre"
    const val PHOTO = "photo"
    const val REGLAGES = "reglages"
    const val PRINTER = "imprimante"
    const val CATALOG = "catalogue"
    const val GAGES = "gages"
    const val STUDENT = "eleve/"

    fun title(route: String) = when {
        route == VERBALISER -> "PVLycée"
        route == REGISTRE -> "Registre"
        route == PHOTO -> "Photo"
        route == REGLAGES -> "Réglages"
        route == PRINTER -> "Imprimante"
        route == CATALOG -> "Catalogue des infractions"
        route == GAGES -> "Gages"
        route.startsWith(STUDENT) -> "Fiche élève"
        else -> "PVLycée"
    }

    fun tabOf(route: String) = when {
        route == VERBALISER -> VERBALISER
        route == PHOTO -> PHOTO
        route == REGISTRE || route.startsWith(STUDENT) -> REGISTRE
        else -> REGLAGES
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PvAppUi() {
    var stack by rememberSaveable { mutableStateOf(listOf(Routes.VERBALISER)) }
    val route = stack.last()
    fun push(r: String) { stack = stack + r }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    fun tab(r: String) { stack = listOf(r) }

    val verbaliserVm: VerbaliserViewModel = viewModel()
    val registreVm: RegistreViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val photoVm: PhotoViewModel = viewModel()
    val status by verbaliserVm.printers.status.collectAsStateWithLifecycle()

    BackHandler(enabled = stack.size > 1) { pop() }
    LaunchedEffect(route) { if (route == Routes.VERBALISER) verbaliserVm.printers.refreshStatus() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(Routes.title(route), fontFamily = Oswald, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (stack.size > 1) {
                            IconButton(onClick = { pop() }) {
                                Icon(painterResource(R.drawable.ic_back), contentDescription = "Retour")
                            }
                        }
                    },
                    actions = { PrinterIndicator(status, onClick = { if (route != Routes.PRINTER) push(Routes.PRINTER) }) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PvColors.Navy,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
                HazardStripe()
            }
        },
        bottomBar = {
            NavigationBar(containerColor = PvColors.Navy) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PvColors.Navy,
                    selectedTextColor = PvColors.Yellow,
                    indicatorColor = PvColors.Yellow,
                    unselectedIconColor = Color.White,
                    unselectedTextColor = Color.White,
                )
                val current = Routes.tabOf(route)
                NavigationBarItem(
                    selected = current == Routes.VERBALISER, onClick = { tab(Routes.VERBALISER) },
                    icon = { Icon(painterResource(R.drawable.ic_ticket), null) }, label = { Text("Verbaliser") }, colors = colors,
                )
                NavigationBarItem(
                    selected = current == Routes.REGISTRE, onClick = { tab(Routes.REGISTRE) },
                    icon = { Icon(painterResource(R.drawable.ic_book), null) }, label = { Text("Registre") }, colors = colors,
                )
                NavigationBarItem(
                    selected = current == Routes.PHOTO, onClick = { tab(Routes.PHOTO) },
                    icon = { Icon(painterResource(R.drawable.ic_camera), null) }, label = { Text("Photo") }, colors = colors,
                )
                NavigationBarItem(
                    selected = current == Routes.REGLAGES, onClick = { tab(Routes.REGLAGES) },
                    icon = { Icon(painterResource(R.drawable.ic_settings), null) }, label = { Text("Réglages") }, colors = colors,
                )
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        Column(m) {
            when {
                route == Routes.VERBALISER -> VerbaliserScreen(
                    verbaliserVm,
                    onOpenPrinter = { push(Routes.PRINTER) },
                    onOpenRegistre = { tab(Routes.REGISTRE) },
                )
                route == Routes.PHOTO -> PhotoScreen(photoVm, onOpenPrinter = { push(Routes.PRINTER) })
                route == Routes.REGISTRE -> RegistreScreen(registreVm, onOpenStudent = { push(Routes.STUDENT + it) })
                route.startsWith(Routes.STUDENT) -> StudentDetailScreen(
                    registreVm,
                    route.removePrefix(Routes.STUDENT).toLongOrNull() ?: -1,
                    onDeleted = { pop() },
                )
                route == Routes.REGLAGES -> SettingsScreen(
                    settingsVm,
                    onOpenPrinter = { push(Routes.PRINTER) },
                    onOpenCatalog = { push(Routes.CATALOG) },
                    onOpenGages = { push(Routes.GAGES) },
                )
                route == Routes.PRINTER -> PrinterScreen(settingsVm)
                route == Routes.CATALOG -> CatalogScreen(settingsVm)
                route == Routes.GAGES -> GagesScreen(settingsVm)
            }
        }
    }
}
