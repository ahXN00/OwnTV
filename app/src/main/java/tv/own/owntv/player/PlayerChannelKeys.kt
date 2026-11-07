package tv.own.owntv.player

import tv.own.owntv.core.settings.PlayerRemoteOptions

/**
 * The zap step for a channel key in the player: CH+ and D-pad Up ([up]) go to the next channel (+1),
 * CH− and Down to the previous one (−1), unless "Swap channel up/down" is on. Every channel key in the
 * player goes through here, so the swap applies to all of them alike.
 */
fun zapDirection(up: Boolean, options: PlayerRemoteOptions): Int = if (up != options.swapChannelKeys) 1 else -1

/** Whether number keys type a channel number: the Channel numbers setting and "Number keys tune channels" both on. */
fun numberKeysTune(channelNumbersOn: Boolean, options: PlayerRemoteOptions): Boolean =
    channelNumbersOn && options.numberKeysTune
