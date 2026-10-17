package tv.own.owntv.features.epg

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.core.theme.ThemeMode

@RunWith(AndroidJUnit4::class)
class EpgRecompositionTest {
    @get:Rule val rule = createComposeRule()

    @Before fun enableCounter() {
        EpgRecompositionCounter.enabled = true
        EpgRecompositionCounter.clear()
    }

    @Test fun cursorAndScrollSkipOtherRows_upDownTouchesAtMostTwo() {
        val now = 1_700_000_000_000L
        val channels = (1L..3L).map { id ->
            ChannelEntity(id = id, sourceId = 1, categoryId = 1, name = "Ch $id", streamUrl = "http://x/$id", remoteId = "$id")
        }
        val programmes = channels.map { ch ->
            listOf(EpgProgrammeEntity(sourceId = 1, epgChannelId = "${ch.id}", startMs = now - 1_800_000, stopMs = now + 1_800_000, title = "Show ${ch.id}"))
        }
        val scroll = mutableIntStateOf(0)
        val cursor = mutableLongStateOf(now)
        val focused = mutableIntStateOf(0)
        val readScroll = { scroll.intValue }
        val requesters = List(channels.size) { FocusRequester() }
        val onEnter = {}
        val onCursor: (Long) -> Unit = {}
        val onShow: (EpgProgrammeEntity?) -> Unit = {}
        val onStep: (Int) -> Boolean = { true }
        val onOk: (EpgProgrammeEntity?) -> Unit = {}
        val onMenu: (EpgProgrammeEntity?) -> Unit = {}

        rule.setContent {
            OwnTVTheme(ThemeMode.DARK, AccentColor.TEAL, systemInDarkTheme = true) {
                Column {
                    channels.forEachIndexed { index, channel ->
                        GuideRow(
                            vm = null,
                            rowIndex = index,
                            channel = channel,
                            name = channel.name,
                            dot = null,
                            labelWidth = 120.dp,
                            numberWidth = 40.dp,
                            windowStart = now - 3_600_000,
                            windowEnd = now + 86_400_000,
                            now = now,
                            scrollPx = readScroll,
                            focusRequester = requesters[index],
                            selectedRow = index == focused.intValue,
                            cursorTime = if (index == focused.intValue) cursor.longValue else -1L,
                            cellMode = false,
                            onEnterCell = onEnter,
                            recordingStarts = emptySet(),
                            reminderStarts = emptySet(),
                            onFocused = {},
                            onCursor = onCursor,
                            onShow = onShow,
                            onStep = onStep,
                            onOk = onOk,
                            onMenu = onMenu,
                            fixedProgrammes = programmes[index],
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.runOnIdle {
            EpgRecompositionCounter.clear()
            scroll.intValue = 240
            cursor.longValue = now + 1_800_000
        }
        rule.waitForIdle()
        channels.drop(1).forEach { ch ->
            assertEquals(0, EpgRecompositionCounter.count(ch.id))
        }

        rule.runOnIdle {
            EpgRecompositionCounter.clear()
            focused.intValue = 1
        }
        rule.waitForIdle()
        val touched = channels.count { EpgRecompositionCounter.count(it.id) > 0 }
        assertTrue("up/down recomposed $touched rows", touched <= 2)
    }
}
