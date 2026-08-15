package org.akanework.gramophone.logic

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.akanework.gramophone.db.AppDatabase
import org.akanework.gramophone.db.GramophoneDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): GramophoneDatabase =
        AppDatabase.newInstance(context)

}