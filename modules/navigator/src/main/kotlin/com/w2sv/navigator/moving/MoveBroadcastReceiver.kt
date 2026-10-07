package com.w2sv.navigator.moving

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.w2sv.androidutils.content.intent
import com.w2sv.navigator.domain.moving.MoveOperation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
internal class MoveBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var fileMover: FileMover

    override fun onReceive(context: Context, intent: Intent) {
        val operation = MoveOperation<MoveOperation>(intent)
        fileMover.invoke(operation, context)
    }

    companion object {
        fun sendBroadcast(operation: MoveOperation, context: Context) {
            context.sendBroadcast(
                intent(
                    moveBundle = operation,
                    context = context
                )
            )
        }

        fun intent(moveBundle: MoveOperation, context: Context): Intent =
            intent<MoveBroadcastReceiver>(context)
                .putExtra(MoveOperation.EXTRA, moveBundle)
    }
}
