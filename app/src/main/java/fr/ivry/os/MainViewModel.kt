package fr.ivry.os

import android.app.Application
import android.os.Bundle
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.ivry.os.data.AppEntry
import fr.ivry.os.data.AppsRepository
import fr.ivry.os.data.DataEngine
import fr.ivry.os.data.Keys
import fr.ivry.os.data.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val appCtx: Application get() = getApplication()

    val settings = SettingsRepo(app)
    val engine = DataEngine(app, settings, viewModelScope)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    /** Application en cours de lancement (déclenche la transition plein écran). */
    val pendingLaunch = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { settings.firstRun() }
        engine.start()
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) { _apps.value = AppsRepository.load(appCtx) }
    }

    fun requestLaunch(pkg: String) { pendingLaunch.value = pkg }

    fun doLaunch(pkg: String) {
        viewModelScope.launch { settings.recordLaunch(pkg) }
        if (!AppsRepository.launch(appCtx, pkg)) AppsRepository.openStore(appCtx, pkg)
    }

    fun openStore(pkg: String) { AppsRepository.openStore(appCtx, pkg) }

    /** « TV en direct » ouvre myCANAL, seule source de TV en direct prévue. */
    fun launchLive() {
        val c = AppsRepository.findCanal(apps.value)
        if (c != null) requestLaunch(c.pkg) else openStore(AppsRepository.CANAL_STORE)
    }

    fun <T> set(key: Preferences.Key<T>, value: T) { viewModelScope.launch { settings.set(key, value) } }
    fun toggleFavorite(pkg: String) { viewModelScope.launch { settings.toggleFavorite(pkg) } }
    fun moveFavorite(pkg: String, delta: Int) { viewModelScope.launch { settings.moveFavorite(pkg, delta) } }

    /** Configuration par ADB : adb shell am start -n fr.ivry.os/.MainActivity --es proxyUrl "https://…" */
    fun applyExtras(ex: Bundle) {
        val map = mapOf(
            "proxyUrl" to Keys.proxyUrl, "proxyToken" to Keys.proxyToken,
            "metroStop" to Keys.metroStop, "metroLine" to Keys.metroLine, "metroFilter" to Keys.metroFilter,
            "rerStop" to Keys.rerStop, "rerLine" to Keys.rerLine, "rerFilter" to Keys.rerFilter,
            "velibCode" to Keys.velibCode,
        )
        viewModelScope.launch {
            map.forEach { (name, key) -> ex.getString(name)?.let { settings.set(key, it.trim()) } }
        }
    }
}
