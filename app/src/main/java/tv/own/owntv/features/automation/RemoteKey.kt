package tv.own.owntv.features.automation

import android.view.KeyEvent

/** The remote buttons the API may press, by the names clients send. Nothing outside this list is sent. */
enum class RemoteKey(val keyCode: Int) {
    UP(KeyEvent.KEYCODE_DPAD_UP),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
    OK(KeyEvent.KEYCODE_DPAD_CENTER),
    BACK(KeyEvent.KEYCODE_BACK),
    MENU(KeyEvent.KEYCODE_MENU),
    INFO(KeyEvent.KEYCODE_INFO),
    GUIDE(KeyEvent.KEYCODE_GUIDE),
    CHANNEL_UP(KeyEvent.KEYCODE_CHANNEL_UP),
    CHANNEL_DOWN(KeyEvent.KEYCODE_CHANNEL_DOWN),
    LAST_CHANNEL(KeyEvent.KEYCODE_LAST_CHANNEL),
    PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
    PLAY(KeyEvent.KEYCODE_MEDIA_PLAY),
    PAUSE(KeyEvent.KEYCODE_MEDIA_PAUSE),
    FAST_FORWARD(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD),
    REWIND(KeyEvent.KEYCODE_MEDIA_REWIND),
    NEXT(KeyEvent.KEYCODE_MEDIA_NEXT),
    PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
    CAPTIONS(KeyEvent.KEYCODE_CAPTIONS),
    RED(KeyEvent.KEYCODE_PROG_RED),
    GREEN(KeyEvent.KEYCODE_PROG_GREEN),
    YELLOW(KeyEvent.KEYCODE_PROG_YELLOW),
    BLUE(KeyEvent.KEYCODE_PROG_BLUE),
    DIGIT_0(KeyEvent.KEYCODE_0),
    DIGIT_1(KeyEvent.KEYCODE_1),
    DIGIT_2(KeyEvent.KEYCODE_2),
    DIGIT_3(KeyEvent.KEYCODE_3),
    DIGIT_4(KeyEvent.KEYCODE_4),
    DIGIT_5(KeyEvent.KEYCODE_5),
    DIGIT_6(KeyEvent.KEYCODE_6),
    DIGIT_7(KeyEvent.KEYCODE_7),
    DIGIT_8(KeyEvent.KEYCODE_8),
    DIGIT_9(KeyEvent.KEYCODE_9),
    ;

    companion object {
        const val MAX_PER_CALL = 50

        /** "up", "OK", "channel-up" and "0" all read; null for anything else. */
        fun parse(name: String): RemoteKey? {
            val normalized = name.trim().uppercase().replace('-', '_')
            if (normalized.length == 1 && normalized[0].isDigit()) return valueOf("DIGIT_$normalized")
            return entries.firstOrNull { it.name == normalized }
        }
    }
}
