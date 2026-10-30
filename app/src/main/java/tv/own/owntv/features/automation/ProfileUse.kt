package tv.own.owntv.features.automation

import tv.own.owntv.core.database.entity.ProfileEntity

/** How `POST profiles/{id}/use` went. */
enum class ProfileUse {
    NOT_FOUND,

    /** The profile has a PIN, which only the person at the TV may enter. */
    LOCKED,

    /** During setup: the profile is the one setup will open ([SetupProfile.choose]); nothing on screen changes. */
    CHOSEN,

    /** During setup, while no device paired from the "Set up by Automation" page ([SetupAccess.CLOSED]). */
    SETUP_CLOSED,

    /** A private screen ([SensitiveScreens]) is open on the TV, so the profile is not switched under it. */
    PRIVATE,

    /** The profile is already open. */
    ACTIVE,

    /** The profile is opening ([ProfileSwitch]). */
    SWITCHING,
    ;

    companion object {
        /**
         * What using [profile] does. During setup ([setup] not [SetupAccess.DONE]) it changes no screen, only
         * which profile setup will open, and only for a device paired from the setup page, private itself,
         * which does not refuse it; afterwards it does switch, and a private screen does. [openId] is the
         * profile open on the TV now ([AutomationProfile.id]).
         */
        fun of(profile: ProfileEntity?, setup: SetupAccess, openId: Long?, privateOpen: Boolean): ProfileUse = when {
            profile == null -> NOT_FOUND
            profile.pinHash != null -> LOCKED
            setup == SetupAccess.OPEN -> CHOSEN
            setup == SetupAccess.CLOSED -> SETUP_CLOSED
            privateOpen -> PRIVATE
            profile.id == openId -> ACTIVE
            else -> SWITCHING
        }
    }
}
