package tv.own.owntv.features.categorylock

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.model.MediaType

/**
 * [profileId]'s browse customizations for [type], led by null until the store has read them. A list
 * built from the empty default would show a locked category's items, so a section's item lists wait
 * for the first real value, and a profile switch starts the wait again. No profile has nothing to read.
 */
fun CustomizationStore.visibleOnceRead(profileId: Long, type: MediaType): Flow<SectionCustomizations?> =
    if (profileId < 0) flowOf(SectionCustomizations())
    else flow {
        emit(null)
        emitAll(observeVisible(profileId, type))
    }
