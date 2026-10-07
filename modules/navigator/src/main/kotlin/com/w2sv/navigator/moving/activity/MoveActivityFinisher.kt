package com.w2sv.navigator.moving.activity

import android.app.Activity
import com.w2sv.navigator.domain.moving.MoveResult
import com.w2sv.navigator.postmove.MoveOperationSummary
import com.w2sv.navigator.postmove.MoveSummaryChannel
import com.w2sv.navigator.postmove.loggingTrySend
import javax.inject.Inject

class MoveActivityFinisher @Inject constructor(private val moveSummaryChannel: MoveSummaryChannel) {

    fun finishOnError(activity: Activity, error: MoveResult) {
        moveSummaryChannel.loggingTrySend(MoveOperationSummary(error))
        activity.finishAndRemoveTask()
    }
}
