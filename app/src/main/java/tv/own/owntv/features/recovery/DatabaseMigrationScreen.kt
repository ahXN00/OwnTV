package tv.own.owntv.features.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.OwnTVSpinner

/**
 * Shown while the database is upgraded to a new version's format ([tv.own.owntv.core.database.DatabaseUpgrade]):
 * on a large catalogue the first start after such an update takes a minute or more, and a black screen
 * for that long looks like a hang. Plain sizes, like the recovery screen: it is drawn before any setting
 * has been read.
 */
@Composable
fun DatabaseMigrationScreen() {
    // The TV's own idle timer would otherwise start a screensaver or count down to standby mid-migration —
    // the one thing this screen asks the user not to do.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 820.dp).padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OwnTVSpinner()
            Text(stringResource(R.string.database_migration_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(stringResource(R.string.database_migration_body), fontSize = 18.sp, color = Color(0xFFCFCFD6))
            Text(stringResource(R.string.database_migration_keep), fontSize = 16.sp, color = Color(0xFF8A8A94))
        }
    }
}
