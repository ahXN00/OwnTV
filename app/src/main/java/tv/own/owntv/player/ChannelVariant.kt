package tv.own.owntv.player

import androidx.compose.runtime.Immutable
import tv.own.owntv.core.database.entity.ChannelEntity

@Immutable
data class ChannelVariant(
    val channel: ChannelEntity,
    val qualityLabel: String,      // Ej: "FHD", "HD", "SD", "4K", "STD"
    val qualityRank: Int,          // Para ordenar de mayor a menor calidad (4K > FHD > HD > SD)
    val categoryName: String? = null,
    val sourceName: String? = null,
)

object ChannelVariantHelper {

    // Detecta etiquetas de calidad, variantes o servidores habituales en listas IPTV
    private val QUALITY_REGEX = Regex(
        """\b(UHD|4K|2160p|FHD|1080p|1080i|HD|720p|SD|576p|480p|HEVC|H\.?265|50fps|60fps|RAW|VIP|BACKUP|ALT|SERVER\s*\d*|SERV\s*\d*|OPT\s*\d*|MULTI|M3U|DIRECTO)\b""",
        RegexOption.IGNORE_CASE
    )

    // Elimina prefijos de país/categoría típicos como "[ES]", "(ES)", "ES |", "SPAIN :", "M3U -", "IPTV •"
    private val PREFIX_REGEX = Regex(
        """^(\s*(\[[^\]]*]|\([^)]*\)|[A-Za-z0-9_\-]{2,10}\s*[|:\-•]\s*))+""",
        RegexOption.IGNORE_CASE
    )

    // Elimina caracteres especiales y espacios para dejar solo la raíz alfanumérica
    private val NON_ALPHANUM_REGEX = Regex("""[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑüÜ]+""")

    /**
     * Convierte "SPAIN | DAZN LA LIGA FHD (1080p)" -> "daznlaliga"
     */
    fun getBaseName(rawName: String): String {
        return rawName
            .replace(PREFIX_REGEX, "")
            .replace(QUALITY_REGEX, "")
            .replace(NON_ALPHANUM_REGEX, "")
            .trim()
            .lowercase()
    }

    /**
     * Extrae una palabra clave limpia para buscar candidatos en ChannelDao.searchListDetailed(...)
     * Extrae las palabras principales eliminando sufijos/prefijos de servidor/calidad.
     */
    fun getSearchKeyword(rawName: String): String {
        val clean = rawName
            .replace(PREFIX_REGEX, "")
            .replace(QUALITY_REGEX, "")
            .replace(Regex("""[|:\-\[\]()]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val words = clean.split(" ").filter { it.length >= 2 }
        return if (words.isNotEmpty()) words.take(3).joinToString(" ") else clean
    }

    /**
     * Extrae la etiqueta de calidad visible para el botón y el submenú ("FHD", "HD · HEVC", etc.)
     */
    fun extractQualityBadge(rawName: String): String {
        val matches = QUALITY_REGEX.findAll(rawName)
            .map { it.value.uppercase() }
            .distinct()
            .toList()
        return if (matches.isNotEmpty()) matches.joinToString(" · ") else "STD"
    }

    /**
     * Asigna una puntuación para ordenar siempre de mejor a peor calidad en el submenú
     */
    fun calculateQualityRank(rawName: String): Int {
        val upper = rawName.uppercase()
        var score = 0
        when {
            upper.contains("UHD") || upper.contains("4K") || upper.contains("2160") -> score += 400
            upper.contains("FHD") || upper.contains("1080") -> score += 300
            upper.contains("HD") || upper.contains("720") -> score += 200
            upper.contains("SD") || upper.contains("576") || upper.contains("480") -> score += 100
            else -> score += 150
        }
        if (upper.contains("RAW") || upper.contains("50FPS") || upper.contains("60FPS")) score += 30
        if (upper.contains("HEVC") || upper.contains("265")) score += 20
        if (upper.contains("VIP")) score += 10
        if (upper.contains("BACKUP")) score -= 50
        return score
    }
}
