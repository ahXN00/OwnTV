package tv.own.owntv.features.automation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity

/** The profiles table in memory, oldest first, with ids handed out the way Room hands them out. */
class FakeProfileDao : ProfileDao {
    private val rows = MutableStateFlow<List<ProfileEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ProfileEntity>> = rows

    override suspend fun getAllOnce(): List<ProfileEntity> = rows.value

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun getById(id: Long): ProfileEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeById(id: Long): Flow<ProfileEntity?> = rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun setAvatar(id: Long, avatarId: Int) = edit(id) { it.copy(avatarId = avatarId) }

    override suspend fun setAvatarPath(id: Long, path: String?) = edit(id) { it.copy(avatarPath = path) }

    override suspend fun count(): Int = rows.value.size

    override suspend fun insert(profile: ProfileEntity): Long {
        val id = if (profile.id == 0L) nextId else profile.id
        nextId = maxOf(nextId, id + 1)
        rows.value = rows.value.filterNot { it.id == id } + profile.copy(id = id)
        return id
    }

    override suspend fun update(profile: ProfileEntity) = edit(profile.id) { profile }

    override suspend fun delete(profile: ProfileEntity) {
        rows.value = rows.value.filterNot { it.id == profile.id }
    }

    override suspend fun setPin(id: Long, pinHash: String?) = edit(id) { it.copy(pinHash = pinHash) }

    private fun edit(id: Long, change: (ProfileEntity) -> ProfileEntity) {
        rows.value = rows.value.map { if (it.id == id) change(it) else it }
    }
}
