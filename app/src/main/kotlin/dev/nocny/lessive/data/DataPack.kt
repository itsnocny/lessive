package dev.nocny.lessive.data

import android.content.Context
import dev.nocny.lessive.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Candy's program names and machine definitions, present only when the private :datapack
 * module is part of the build.
 */
class DataPack(
    val programs: List<CatalogProgram>,
    /** Machine definitions shipped with the data pack. */
    val machines: List<MachineProfile>,
) {

    fun program(key: String?) = key?.let { k -> programs.firstOrNull { it.key == k } }

    fun searchPrograms(query: String): List<CatalogProgram> {
        val q = query.trim().lowercase()
        val unique = programs.distinctBy { it.fr.lowercase() }
        if (q.isEmpty()) return unique
        return unique.filter { q in it.fr.lowercase() || q in it.en.lowercase() }
    }

    /** The official profile for a machine, matched by appliance code or model. */
    fun machineFor(model: String?, applianceCode: String? = null): MachineProfile? {
        fun norm(s: String?) = s?.uppercase()?.filter { it.isLetterOrDigit() }.orEmpty()
        return machines.firstOrNull { applianceCode != null && it.applianceCode == applianceCode }
            ?: machines.firstOrNull { norm(it.model).isNotEmpty() && norm(it.model) == norm(model) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): DataPack? = runCatching {
            val assets = context.assets
            val programs = assets.open("datapack/programs.json").bufferedReader().use {
                json.decodeFromString<List<CatalogProgram>>(it.readText())
            }
            val machines = assets.list("datapack/machines").orEmpty().filter { it.endsWith(".json") }.mapNotNull { file ->
                runCatching {
                    assets.open("datapack/machines/$file").bufferedReader().use { json.decodeFromString<MachineProfile>(it.readText()) }
                }.getOrNull()
            }
            DataPack(programs, machines)
        }.getOrNull()
    }
}

@Serializable
data class CatalogProgram(
    val key: String,
    val fr: String,
    val en: String,
    val descriptionFr: String? = null,
    val descriptionEn: String? = null,
)


/**
 * Program names follow the phone's language when the data pack knows the program. A name
 * typed in the dial editor has no catalog key and stays as typed.
 */
fun MachineProfile.localized(pack: DataPack?, context: Context): MachineProfile {
    val french = context.isFrench()
    return copy(programs = programs.map { p ->
        val key = p.catalogKey ?: return@map p
        val name = pack?.program(key)?.let { if (french) it.fr else it.en }
            ?: if ("AUTO_CLEAN" in key) context.getString(R.string.auto_clean) else p.name
        p.copy(name = name)
    })
}

fun CatalogProgram.description(french: Boolean) = if (french) descriptionFr else descriptionEn ?: descriptionFr

fun Context.isFrench() = resources.configuration.locales[0].language == "fr"
