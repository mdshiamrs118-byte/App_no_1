package com.w2sv.navigator.di

import android.content.ContentResolver
import android.content.Context
import com.w2sv.navigator.FileNavigator
import com.w2sv.navigator.postmove.MoveSummaryChannel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow

@InstallIn(SingletonComponent::class)
@Module
internal object FileNavigatorModule {

    @FileNavigatorIsRunning
    @Provides
    fun fileNavigatorIsRunning(status: FileNavigator.Status): StateFlow<Boolean> =
        status.isRunning

    @Singleton
    @Provides
    fun moveSummaryChannel(): MoveSummaryChannel =
        Channel(Channel.BUFFERED)

    @Provides
    fun contentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
