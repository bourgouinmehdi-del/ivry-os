package fr.ivry.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ivry.os.MainViewModel
import fr.ivry.os.data.AppEntry
import fr.ivry.os.data.AppsRepository
import fr.ivry.os.data.Prefs

@Composable
fun AppsScreen(vm: MainViewModel, prefs: Prefs, apps: List<AppEntry>, section: Section) {
    val p = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var actionFor by remember { mutableStateOf<AppEntry?>(null) }

    val list = remember(apps, prefs.favorites, prefs.launchCounts, section, query) {
        val base = when (section) {
            Section.CINEMA -> AppsRepository.cinema(apps, prefs)
            Section.FAVORITES -> prefs.favorites.mapNotNull { f -> apps.firstOrNull { it.pkg == f } }
            else -> AppsRepository.byUsage(apps, prefs)
        }
        if (query.isBlank()) base else base.filter { it.label.contains(query, ignoreCase = true) }
    }
    val (title, sub) = when (section) {
        Section.CINEMA -> "Cinéma" to "Applications vidéo installées"
        Section.FAVORITES -> "Favoris" to "Dans l'ordre affiché sur l'accueil"
        else -> "Applications" to "Classées par fréquence d'utilisation"
    }

    Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 26.dp, top = 18.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.uppercase(), fontSize = 10.sp, letterSpacing = 1.6.sp, color = p.textDim, fontFamily = Condensed)
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = p.text, fontFamily = Condensed)
            }
            ActionButton("Rechercher", Icons.Default.Search) { showSearch = true }
            Spacer(Modifier.width(8.dp))
            ActionButton(if (editMode) "Terminer" else "Organiser", if (editMode) Icons.Default.Check else Icons.Default.Edit) {
                editMode = !editMode
            }
        }
        if (query.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filtre : « $query »", fontSize = 12.sp, color = p.focus)
                Spacer(Modifier.width(10.dp))
                ActionButton("Effacer") { query = "" }
            }
        }
        if (editMode) {
            Spacer(Modifier.height(6.dp))
            Text("Mode organisation : OK sur une application pour la mettre en favori ou la déplacer.",
                fontSize = 12.sp, color = p.focus)
        }
        Spacer(Modifier.height(12.dp))
        if (list.isEmpty()) {
            Text(
                when (section) {
                    Section.FAVORITES -> "Aucun favori. Ouvre « Applications », active « Organiser » puis ajoute des favoris."
                    else -> "Aucune application trouvée."
                },
                fontSize = 13.sp, color = p.textDim,
            )
        }
        LazyVerticalGrid(
            GridCells.Fixed(5), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(4.dp),
        ) {
            items(list, key = { it.pkg }) { app ->
                AppTile(app, app.pkg in prefs.favorites, Modifier.aspectRatio(16f / 9f)) {
                    if (editMode) actionFor = app else vm.requestLaunch(app.pkg)
                }
            }
        }
    }

    if (showSearch) EditDialog("Rechercher une application", query, onDismiss = { showSearch = false }) {
        query = it.trim(); showSearch = false
    }

    actionFor?.let { app ->
        val fav = app.pkg in prefs.favorites
        val idx = prefs.favorites.indexOf(app.pkg)
        AlertDialog(
            onDismissRequest = { actionFor = null },
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton(onClick = { actionFor = null; vm.requestLaunch(app.pkg) }) { Text("Lancer") }
                    TextButton(onClick = { vm.toggleFavorite(app.pkg); actionFor = null }) {
                        Text(if (fav) "Retirer des favoris" else "Ajouter aux favoris")
                    }
                    if (fav && idx > 0) TextButton(onClick = { vm.moveFavorite(app.pkg, -1); actionFor = null }) {
                        Text("Avancer d'une place")
                    }
                    if (fav && idx < prefs.favorites.size - 1) TextButton(onClick = { vm.moveFavorite(app.pkg, 1); actionFor = null }) {
                        Text("Reculer d'une place")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { actionFor = null }) { Text("Fermer") } },
        )
    }
}
