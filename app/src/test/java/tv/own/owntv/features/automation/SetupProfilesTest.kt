package tv.own.owntv.features.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity

class SetupProfilesTest {
    private val tv = SetupHarness()
    private var hold: CompletableDeferred<Unit>? = null
    private val switched = mutableListOf<Long>()

    private val backend = AutomationProfiles(
        profileDao = tv.profileDao,
        create = { name, avatar, kids ->
            hold?.await()
            tv.profileDao.insert(ProfileEntity(name = name, avatarColor = 0, avatarId = avatar, isKids = kids))
        },
        target = tv.target,
        setup = tv.setup,
        sensitive = SensitiveScreens(),
        switchTo = { switched += it },
    )

    @Test
    fun `a profile is added only while a computer paired from the page is setting up`() = runBlocking {
        assertEquals(ProfileAdded.SetupClosed, backend.add(NewProfile("Family")))
        tv.session.start()
        assertEquals(ProfileAdded.SetupClosed, backend.add(NewProfile("Family")))
        tv.devices += SetupHarness.PAIRED
        assertTrue(backend.add(NewProfile("Family")) is ProfileAdded.Added)
        tv.session.close()
        assertEquals(ProfileAdded.SetupClosed, backend.add(NewProfile("Kids", kids = true)))
        assertEquals(listOf("Family"), tv.profileDao.getAllOnce().map { it.name })
    }

    @Test
    fun `once setup is done profiles are added on the TV`() = runBlocking {
        tv.pair()
        tv.activeProfileId.value = tv.profile("Family")
        assertEquals(ProfileAdded.AfterSetup, backend.add(NewProfile("Kids")))
        assertEquals(1, tv.profileDao.count())
    }

    @Test
    fun `a name is taken in any letter case`() = runBlocking {
        tv.profile("Family")
        tv.pair()
        assertEquals(ProfileAdded.Taken, backend.add(NewProfile("FAMILY")))
    }

    @Test
    fun `two adds of one name at once make one profile`() = runBlocking {
        tv.pair()
        val release = CompletableDeferred<Unit>().also { hold = it }
        val first = async(Dispatchers.Unconfined) { backend.add(NewProfile("Family")) }
        val second = async(Dispatchers.Unconfined) { backend.add(NewProfile("family")) }
        release.complete(Unit)
        assertTrue(first.await() is ProfileAdded.Added)
        assertEquals(ProfileAdded.Taken, second.await())
        assertEquals(1, tv.profileDao.count())
    }

    @Test
    fun `a cancel during an add lands after it`() = runBlocking {
        tv.pair()
        val release = CompletableDeferred<Unit>().also { hold = it }
        val added = async(Dispatchers.Unconfined) { backend.add(NewProfile("Family")) }
        tv.session.close()
        assertTrue("the cancel waits for the add", tv.enabled)
        release.complete(Unit)
        assertTrue(added.await() is ProfileAdded.Added)
        assertFalse(tv.enabled)
        assertFalse(SetupHarness.PAIRED in tv.devices)
        assertEquals("Cancel keeps the profile the API made", 1, tv.profileDao.count())
    }

    @Test
    fun `the playlist routes act for the setup profile only while the visit is open`() = runBlocking {
        val family = tv.profile("Family")
        assertNull(tv.target.id())
        tv.session.start()
        assertNull("nothing paired from the page yet", tv.target.id())
        tv.devices += SetupHarness.PAIRED
        assertEquals(family, tv.target.id())
        tv.session.close()
        assertNull(tv.target.id())
    }

    @Test
    fun `choosing a profile during setup makes it the one setup opens, and never one with a PIN`() = runBlocking {
        tv.profile("Family")
        val kids = tv.profile("Kids")
        val parents = tv.profile("Parents", pinHash = "hash")
        tv.pair()
        assertEquals(ProfileUse.CHOSEN, backend.use(kids))
        assertEquals(kids, tv.target.id())
        assertEquals(ProfileUse.LOCKED, backend.use(parents))
        assertEquals(kids, tv.target.id())
        assertTrue(switched.isEmpty())
    }

    @Test
    fun `once a profile is open the playlist routes act for it, not the setup one`() = runBlocking {
        tv.profile("Family")
        tv.pair()
        val open = PlaylistTarget(openId = { OPEN }, openPlaylists = { emptyList() }, setup = tv.setup)
        assertEquals(OPEN, open.id())
    }

    private companion object {
        const val OPEN = 7L
    }
}
