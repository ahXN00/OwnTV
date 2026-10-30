package tv.own.owntv.features.automation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ProfileEntity

/**
 * Lets "Who's watching?" pick the profile the API opened ([ProfileChoice]), as if it had been chosen
 * there. [onChoose] is the gate's own choice, so a profile comes in the way a press on its tile brings it.
 */
@Composable
fun ApiProfileChoice(profiles: List<ProfileEntity>, choice: ProfileChoice = koinInject(), onChoose: (ProfileEntity) -> Unit) {
    val pending by choice.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending, profiles) {
        val request = pending ?: return@LaunchedEffect
        choice.take(request, profiles)?.let(onChoose)
    }
}
