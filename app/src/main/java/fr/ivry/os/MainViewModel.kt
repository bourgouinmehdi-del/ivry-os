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
import fr.ivry.os.data.UpdateInfo
import fr.ivry.os.data.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    /** État des mises à jour publiées sur GitHub. */
    data class UpdateUi(val info: UpdateInfo? = null, val busy: Boolean = false, val message: String? = null)
    val update = MutableStateFlow(UpdateUi())
    val installedVersion: Int get() = Updater.currentVersion(appCtx)

    /** Application en cours de lancement (déclenche la transition plein écran). */
    val pendingLaunch = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { settings.firstRun() }
        engine.start()
        viewModelScope.launch {
            while (isActive) { checkUpdate(manual = false); delay(6 * 3_600_000L) }
        }
    }

    fun checkUpdate(manual: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (manual) update.value = update.value.copy(busy = true, message = null)
            val r = runCatching { Updater.check(appCtx) }
            update.value = when {
                r.isFailure -> update.value.copy(busy = false, message = if (manual) "Vérification impossible pour le moment" else null)
                r.getOrNull() == null -> UpdateUi(message = if (manual) "IVRY OS est à jour" else null)
                else -> UpdateUi(info = r.getOrNull())
            }
        }
    }

    fun installUpdate() {
        val info = update.value.info ?: return
        if (!Updater.canInstall(appCtx)) {
            update.value = update.value.copy(message = "Autorise IVRY OS à installer des applis, puis réessaie")
            Updater.openInstallPermission(appCtx)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            update.value = update.value.copy(busy = true, message = "Téléchargement…")
            runCatching { Updater.download(appCtx, info) }
                .onSuccess { apk ->
                    update.value = update.value.copy(busy = false, message = null)
                    runCatching { Updater.install(appCtx, apk) }
                        .onFailure { update.value = update.value.copy(message = "Installation impossible : ${it.message}") }
                }
                .onFailure { update.value = update.value.copy(busy = false, message = "Téléchargement impossible") }
        }
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
            "newsUrl" to Keys.newsUrl, "icalUrl" to Keys.icalUrl,
        )
        viewModelScope.launch {
            map.forEach { (name, key) -> ex.getString(name)?.let { settings.set(key, it.trim()) } }
        }
    }
}
