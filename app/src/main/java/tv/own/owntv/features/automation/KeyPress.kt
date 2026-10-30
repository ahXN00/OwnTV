package tv.own.owntv.features.automation

/** How a [PlayerBackend.press] call ended. */
enum class KeyPress {
    /** Every key was pressed. */
    PRESSED,

    /** OwnTV is not the screen in front, so there is nothing to press keys on. */
    NOT_IN_FRONT,

    /** A screen the API must not drive is open ([SensitiveScreens]); keys before it were pressed. */
    PAUSED,

    /** Another call's keys still held the turn after [KeyGate]'s wait; none of this call's keys were pressed. */
    BUSY,
}
