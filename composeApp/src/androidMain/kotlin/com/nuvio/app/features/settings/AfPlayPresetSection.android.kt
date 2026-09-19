package com.nuvio.app.features.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal actual fun AfPlayPresetSection(isTablet: Boolean) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var pendingExportPayload by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val payload = pendingExportPayload
        pendingExportPayload = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult

        runCatching {
            val bytes = payload.encodeToByteArray()
            require(bytes.isNotEmpty()) { "Presetul generat este gol." }
            context.contentResolver.openOutputStream(uri, "w")?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: error("Nu pot deschide fișierul pentru scriere.")
            bytes.size
        }.onSuccess { byteCount ->
            val kb = (byteCount + 1023) / 1024
            status = "Preset exportat: ${kb} KB."
        }.onFailure {
            status = "Export eșuat: ${it.message.orEmpty()}"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Nu pot citi presetul.")
            AfPlayPresetCodec.importPreset(context, payload)
        }.onSuccess {
            status = "Preset importat. Închide complet AF Play și redeschide aplicația."
        }.onFailure {
            status = "Import eșuat: ${it.message.orEmpty()}"
        }
    }

    SettingsSection(
        title = "PRESET AF PLAY",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = "Exportă preset AF Play",
                description = status ?: "Salvează aspectul, addonurile publice, listele TV M3U/JSON și setările aplicației pentru un APK preconfigurat.",
                isTablet = isTablet,
                onClick = {
                    runCatching {
                        AfPlayPresetCodec.exportPreset(context)
                    }.onSuccess { payload ->
                        pendingExportPayload = payload
                        val kb = (payload.encodeToByteArray().size + 1023) / 1024
                        status = "Preset pregătit: ${kb} KB. Alege unde îl salvezi."
                        exportLauncher.launch("AF-Play-Preset.json")
                    }.onFailure {
                        pendingExportPayload = null
                        status = "Generare preset eșuată: ${it.message.orEmpty()}"
                    }
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = "Importă preset AF Play",
                description = "Aplică un preset exportat anterior pe acest dispozitiv.",
                isTablet = isTablet,
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/json", "text/plain"),
                    )
                },
            )
        }
    }
}

internal object AfPlayPresetCodec {
    private const val PRESET_VERSION = 3

    private val preferenceFiles = listOf(
        "nuvio_theme_settings",
        "nuvio_home_catalog_settings",
        "nuvio_player_settings",
        "nuvio_library_display_settings",
        "nuvio_continue_watching_preferences",
        "nuvio_stream_badge_settings",
        "nuvio_poster_card_style",
        "nuvio_discover_selection",
        "nuvio_addons",
        "nuvio_live_tv",
    )

    private val deniedKeyParts = listOf(
        "password",
        "token",
        "secret",
        "api_key",
        "apikey",
        "passkey",
        "credential",
        "authorization",
        "cookie",
        "email",
        "username",
    )

    fun exportPreset(context: Context): String {
        val root = JSONObject()
        root.put("format", "afplay-preset")
        root.put("version", PRESET_VERSION)
        val allPrefs = JSONObject()

        preferenceFiles.forEach { prefsName ->
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val output = JSONObject()

            prefs.all.forEach { (key, rawValue) ->
                if (!isSafeKey(key)) return@forEach
                val sanitized = sanitizeValue(prefsName, key, rawValue) ?: return@forEach
                output.put(key, encodeValue(sanitized))
            }

            if (output.length() > 0) {
                allPrefs.put(prefsName, output)
            }
        }

        root.put("preferences", allPrefs)
        return root.toString(2)
    }

    fun importPreset(context: Context, payload: String) {
        val root = JSONObject(payload)
        require(root.optString("format") == "afplay-preset") {
            "Fișierul nu este un preset AF Play."
        }
        require(root.optInt("version", 0) in 1..PRESET_VERSION) {
            "Versiune preset incompatibilă."
        }

        val allPrefs = root.getJSONObject("preferences")
        val names = allPrefs.keys()
        while (names.hasNext()) {
            val prefsName = names.next()
            if (prefsName !in preferenceFiles) continue

            val data = allPrefs.getJSONObject(prefsName)
            val editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
            val keys = data.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!isSafeKey(key)) continue
                decodeIntoEditor(editor, key, data.getJSONObject(key))
            }
            editor.apply()
        }
    }

    private fun isSafeKey(key: String): Boolean {
        val lowered = key.lowercase()
        return deniedKeyParts.none { it in lowered }
    }

    private fun sanitizeValue(prefsName: String, key: String, raw: Any?): Any? {
        if (raw == null) return null

        if (prefsName == "nuvio_addons") {
            if (key.startsWith("installed_manifest_urls")) {
                return raw.toString()
                    .lineSequence()
                    .mapNotNull(::sanitizePublicUrl)
                    .distinct()
                    .joinToString("\n")
            }
            if (key.startsWith("installed_manifest_enabled_states")) {
                return raw.toString()
                    .lineSequence()
                    .mapNotNull { line ->
                        val url = line.substringBefore('\t').trim()
                        val enabled = line.substringAfter('\t', "true").trim()
                        sanitizePublicUrl(url)?.let { "$it\t$enabled" }
                    }
                    .distinct()
                    .joinToString("\n")
            }
        }

        if (prefsName == "nuvio_live_tv") {
            val loweredKey = key.lowercase()

            if (
                "stalker_settings" in loweredKey ||
                "xtream_settings" in loweredKey
            ) {
                return null
            }

            if (
                "playlist_url" in loweredKey ||
                "playlists_blob" in loweredKey ||
                "favorite_channel_ids_blob" in loweredKey ||
                "last_watched_channel_id" in loweredKey ||
                "navigation_enabled" in loweredKey
            ) {
                return raw
            }
        }

        return when (raw) {
            is String -> sanitizeString(raw)
            is Set<*> -> raw.mapNotNull { it?.toString()?.let(::sanitizeString) }.toSet()
            is Boolean, is Int, is Long, is Float -> raw
            else -> sanitizeString(raw.toString())
        }
    }

    private fun sanitizePublicUrl(value: String): String? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return null
        if (trimmed.contains('@')) return null
        return trimmed.substringBefore('?').substringBefore('#').takeIf { it.isNotBlank() }
    }

    private fun sanitizeString(value: String): String {
        val urlRegex = Regex("""https?://[^\s"']+""")
        return urlRegex.replace(value) { match ->
            match.value.substringBefore('?').substringBefore('#')
        }
    }

    private fun encodeValue(value: Any): JSONObject {
        val item = JSONObject()
        when (value) {
            is String -> {
                item.put("type", "string")
                item.put("value", value)
            }
            is Boolean -> {
                item.put("type", "boolean")
                item.put("value", value)
            }
            is Int -> {
                item.put("type", "int")
                item.put("value", value)
            }
            is Long -> {
                item.put("type", "long")
                item.put("value", value)
            }
            is Float -> {
                item.put("type", "float")
                item.put("value", value.toDouble())
            }
            is Set<*> -> {
                item.put("type", "strings")
                item.put("value", JSONArray(value.map { it.toString() }))
            }
            else -> {
                item.put("type", "string")
                item.put("value", value.toString())
            }
        }
        return item
    }

    private fun decodeIntoEditor(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        item: JSONObject,
    ) {
        when (item.optString("type")) {
            "string" -> editor.putString(key, item.optString("value"))
            "boolean" -> editor.putBoolean(key, item.optBoolean("value"))
            "int" -> editor.putInt(key, item.optInt("value"))
            "long" -> editor.putLong(key, item.optLong("value"))
            "float" -> editor.putFloat(key, item.optDouble("value").toFloat())
            "strings" -> {
                val array = item.optJSONArray("value") ?: JSONArray()
                val values = buildSet {
                    for (i in 0 until array.length()) add(array.optString(i))
                }
                editor.putStringSet(key, values)
            }
        }
    }
}

internal object AfPlayPresetBootstrap {
    private const val ASSET_NAME = "afplay-preset.json"

    fun applyIfFreshInstall(context: Context) {
        val hasExistingConfiguration = listOf(
            "nuvio_theme_settings",
            "nuvio_addons",
            "nuvio_home_catalog_settings",
            "nuvio_player_settings",
        ).any { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()
        }
        if (hasExistingConfiguration) return

        val payload = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return

        runCatching {
            AfPlayPresetCodec.importPreset(context, payload)
        }
    }
}
