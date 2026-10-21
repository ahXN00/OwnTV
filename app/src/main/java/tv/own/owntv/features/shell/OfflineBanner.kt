package tv.own.owntv.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.stage.stageGlass

/** A thin bar shown above the browse UI when the device loses internet. */
@Composable
internal fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .stageGlass(0.dp, overContent = true)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.content_offline_banner),
            style = tv.own.owntv.ui.theme.stageText(25, 600),
            color = tv.own.owntv.ui.theme.StageColors.Text,
        ) }
    }
