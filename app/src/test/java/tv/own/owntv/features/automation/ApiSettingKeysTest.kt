package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiSettingKeysTest {
    private val excluded = listOf(
        "proxy_enabled", "proxy_host", "proxy_user", "proxy_port", "dns_enabled", "dns_host", "dns_doh_url", "dns_port",
        "metadata_server_url", "open_subtitles_server_url", "download_root", "bg_image_path",
    )

    @Test
    fun `keys that point the app elsewhere are not allowed`() {
        excluded.forEach { assertFalse(it, it in ApiSettingKeys.allowed) }
    }

    @Test
    fun `no allowed key looks like an address, a path or the API itself`() {
        val risky = listOf("proxy", "dns", "server", "url", "root", "path", "automation", "tailscale", "customize_pin")
        ApiSettingKeys.allowed.forEach { key -> risky.forEach { word -> assertFalse("$key holds $word", key.contains(word)) } }
    }

    @Test
    fun `everyday settings stay allowed`() {
        listOf("theme_mode", "vod_buffer_secs", "hw_decoding", "sub_scale", "nav_menu_hidden", "ui_language")
            .forEach { assertTrue(it, it in ApiSettingKeys.allowed) }
    }

    @Test
    fun `rejected lists the unknown and excluded keys`() {
        val changes = JSONObject().put("dns_host", "x").put("theme_mode", "DARK").put("made_up", 1)
        assertEquals(setOf("dns_host", "made_up"), ApiSettingKeys.rejected(changes).toSet())
        assertEquals(emptyList<String>(), ApiSettingKeys.rejected(JSONObject().put("theme_mode", "DARK")))
    }

    @Test
    fun `visible drops what the API cannot change`() {
        val all = JSONObject().put("theme_mode", "DARK").put("download_root", "/sdcard").put("proxy_port", 8080)
        assertEquals("""{"theme_mode":"DARK"}""", ApiSettingKeys.visible(all).toString())
    }
}
