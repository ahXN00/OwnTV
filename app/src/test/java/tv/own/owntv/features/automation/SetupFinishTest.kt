package tv.own.owntv.features.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFinishTest {
    private val tv = SetupHarness()
    private val settings = JSONObject().put("ui_language", "").put("ui_zoom_percent", 100).put("font_size_percent", 100)
    private val imported = mutableListOf<JSONObject>()
    private val finished = mutableListOf<Long>()
    private var hold: CompletableDeferred<Unit>? = null
    private val choice = ProfileChoice { 0L }
    private val nav = AutomationNav { 0L }

    // The font size stays as it was, as a value the app would skip on import.
    private val appSettings = object : SettingsBackend {
        override suspend fun export() = JSONObject(settings.toString())
        override suspend fun import(changes: JSONObject) {
            imported += changes
            changes.keys().forEach { if (it != "font_size_percent") settings.put(it, changes.get(it)) }
        }
    }

    private val backend = AutomationSetup(
        setup = tv.setup,
        session = tv.session,
        profileDao = tv.profileDao,
        finishFirstRun = { profileId ->
            hold?.await()
            finished += profileId
            tv.activeProfileId.value = profileId
        },
        appSettings = appSettings,
        choice = choice,
        nav = nav,
    )

    @Test
    fun `finishing applies the values, opens the profile, goes Home and leaves the API on`() = runBlocking {
        val family = tv.profile("Family")
        tv.pair()
        val done = backend.finish(SetupFinish(language = "pt-BR", uiZoom = 150, fontSize = 60)) as SetupFinished.Done
        assertEquals(listOf(family), finished)
        assertEquals(family, done.json.getLong("profileId"))
        assertEquals(NavRequest.Section(NavTarget.HOME, family, 0L), nav.pending.value)
        assertTrue(tv.enabled)
        assertTrue("the computer that set the TV up stays paired", SetupHarness.PAIRED in tv.devices)
        assertNull(tv.session.known.value)
        assertNull("one profile: no Who's watching? to answer", choice.pending.value)
    }

    @Test
    fun `the answer names the values the way the request did`() = runBlocking {
        tv.profile("Family")
        tv.pair()
        val done = backend.finish(SetupFinish(language = "pt-BR", uiZoom = 150, fontSize = 60)) as SetupFinished.Done
        assertEquals(setOf("language", "uiZoom"), names(done.json.getJSONArray("applied")))
        assertEquals(setOf("fontSize"), names(done.json.getJSONArray("notApplied")))
    }

    @Test
    fun `finish is refused before a device paired from the page and after the page closed`() = runBlocking {
        tv.profile("Family")
        assertEquals(SetupFinished.Closed, backend.finish(SetupFinish(uiZoom = 150)))
        tv.session.start()
        assertEquals(SetupFinished.Closed, backend.finish(SetupFinish(uiZoom = 150)))
        tv.devices += SetupHarness.PAIRED
        tv.session.close()
        assertEquals(SetupFinished.Closed, backend.finish(SetupFinish(uiZoom = 150)))
        assertTrue(finished.isEmpty())
        assertTrue(imported.isEmpty())
        assertNull(nav.pending.value)
    }

    @Test
    fun `a second finish is told setup is already done`() = runBlocking {
        tv.profile("Family")
        tv.pair()
        assertTrue(backend.finish(SetupFinish()) is SetupFinished.Done)
        assertEquals(SetupFinished.AlreadyDone, backend.finish(SetupFinish()))
        assertEquals(1, finished.size)
    }

    @Test
    fun `with only profiles that have a PIN there is nothing to finish, and the visit goes on`() = runBlocking {
        tv.profile("Parents", pinHash = "hash")
        tv.pair()
        assertEquals(SetupFinished.NoProfile, backend.finish(SetupFinish()))
        assertTrue(finished.isEmpty())
        assertTrue(tv.session.paired())
    }

    @Test
    fun `Who's watching is told the profile only when it will come up`() = runBlocking {
        tv.profile("Family")
        val kids = tv.profile("Kids")
        tv.pair()
        tv.setup.choose(kids)
        backend.finish(SetupFinish())
        assertEquals(kids, choice.pending.value?.profileId)
    }

    @Test
    fun `a cancel during finish waits for it, and then has nothing to undo`() = runBlocking {
        val family = tv.profile("Family")
        tv.pair()
        val release = CompletableDeferred<Unit>().also { hold = it }
        val result = async(Dispatchers.Unconfined) { backend.finish(SetupFinish()) }
        tv.session.close()
        assertTrue("the cancel waits for the finish", tv.session.paired())
        release.complete(Unit)
        assertTrue(result.await() is SetupFinished.Done)
        assertEquals(listOf(family), finished)
        assertTrue(tv.enabled)
        assertTrue(SetupHarness.PAIRED in tv.devices)
        assertFalse(tv.session.paired())
    }

    private fun names(array: JSONArray): Set<String> =
        (0 until array.length()).mapTo(mutableSetOf()) { array.getString(it) }
}
