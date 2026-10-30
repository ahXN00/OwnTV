package tv.own.owntv.features.automation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFinishRequestTest {
    private val languages = setOf("", "en-US", "pt-BR")

    @Test
    fun `the values go to the settings keys a settings PATCH uses`() {
        val settings = SetupFinish("pt-BR", 80, 120).settings()
        assertEquals("pt-BR", settings.getString("ui_language"))
        assertEquals(80, settings.getInt("ui_zoom_percent"))
        assertEquals(120, settings.getInt("font_size_percent"))
        assertTrue(ApiSettingKeys.rejected(settings).isEmpty())
    }

    @Test
    fun `values left out are left as they are`() {
        assertEquals(0, SetupFinish().settings().length())
    }

    @Test
    fun `an empty language is the system's, and the edges of each range are taken`() {
        val parsed = SetupFinishRequest.parse(JSONObject("""{"language":" ","uiZoom":50,"fontSize":140}"""), languages)
        assertEquals(ParsedFinish.Ok(SetupFinish("", 50, 140)), parsed)
        assertEquals(ParsedFinish.Ok(SetupFinish(null, 150, 60)), SetupFinishRequest.parse(JSONObject("""{"uiZoom":150,"fontSize":60}"""), languages))
    }

    @Test
    fun `a language the wizard does not offer is refused, naming the ones it does`() {
        val parsed = SetupFinishRequest.parse(JSONObject("""{"language":"fr"}"""), languages)
        val reason = (parsed as ParsedFinish.Invalid).reason
        assertTrue(reason, reason.contains("en-US, pt-BR"))
    }
}
