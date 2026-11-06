package tv.own.owntv.features.live

/** What OK on a channel row does in Live TV and the TV Guide. */
enum class OkAction { PREVIEW, WATCH }

/**
 * The "OK previews first" rule both screens share. With the screen's switch on and a preview pane that
 * can play, OK on a channel brings it into the pane; OK on the channel already there opens it full
 * screen. With the switch off, or no pane to play in, OK always opens full screen — a first OK that
 * previews into nothing would look like a dead key.
 *
 * [paneChannelId] is the channel the pane was last asked to play, whether it plays or not: on a panel
 * that allows one stream the pane stays blank and says so, and the second OK must still go full screen.
 */
fun okAction(twoStep: Boolean, paneCanPlay: Boolean, paneChannelId: Long?, okChannelId: Long): OkAction =
    if (twoStep && paneCanPlay && paneChannelId != okChannelId) OkAction.PREVIEW else OkAction.WATCH
