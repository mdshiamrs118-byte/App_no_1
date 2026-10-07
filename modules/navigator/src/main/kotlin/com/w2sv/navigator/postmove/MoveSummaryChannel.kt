package com.w2sv.navigator.postmove

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import slimber.log.e
import slimber.log.i

internal typealias MoveSummaryChannel = Channel<MoveOperationSummary>

fun MoveSummaryChannel.loggingTrySend(operationSummary: MoveOperationSummary) {
    i { "Try sending $operationSummary via MoveSummaryChannel" }
    trySend(operationSummary).onFailure { error ->
        e { "Failed to send summary: $error" }
    }
}
