package tv.own.owntv.features.automation

/** What the first-run "Set up by Automation" page shows, read from the API's own state ([of]). */
sealed interface AutomationSetupStatus {
    /** The API is coming up, or the page has not started yet. */
    data object Starting : AutomationSetupStatus

    /** Listening at [urls], with the PIN on screen. */
    data class Waiting(val pairing: PairingWindow.Open, val urls: List<String>) : AutomationSetupStatus

    /** [deviceName] paired from this page; it finishes setup from there. */
    data class Paired(val deviceName: String) : AutomationSetupStatus

    /** The PIN ran out, or was entered wrong too often, before anything paired. */
    data object Expired : AutomationSetupStatus

    data class Failed(val message: String) : AutomationSetupStatus

    companion object {
        /**
         * [known] is the devices paired before the page opened, or null when it has not started. A
         * device that is not among them paired from here, and that wins over everything else: its PIN
         * has closed by then, which would otherwise read as [Expired].
         */
        fun of(
            known: Set<String>?,
            devices: List<PairedDevice>,
            pairing: PairingWindow.Open?,
            server: AutomationServer.State,
        ): AutomationSetupStatus {
            if (known == null) return Starting
            devices.lastOrNull { it.id !in known }?.let { return Paired(it.name) }
            if (server is AutomationServer.State.Failed) return Failed(server.message)
            if (pairing == null) return Expired
            return if (server is AutomationServer.State.Listening) Waiting(pairing, server.urls) else Starting
        }
    }
}
