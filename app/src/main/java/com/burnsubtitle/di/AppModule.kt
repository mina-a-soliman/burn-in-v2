package com.burnsubtitle.di

import android.content.Context
import androidx.work.WorkManager
import com.burnsubtitle.domain.parser.AssParser
import com.burnsubtitle.domain.parser.SrtParser
import com.burnsubtitle.domain.parser.SubtitleParser
import com.burnsubtitle.domain.parser.VttParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSubtitleParsers(): List<SubtitleParser> {
        return listOf(SrtParser(), VttParser(), AssParser())
    }
}
