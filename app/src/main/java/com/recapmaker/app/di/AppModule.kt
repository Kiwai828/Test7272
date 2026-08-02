package com.recapmaker.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.GsonBuilder
import com.recapmaker.app.BuildConfig
import com.recapmaker.app.data.api.AuthInterceptor
import com.recapmaker.app.data.api.RecapApi
import com.recapmaker.app.data.local.AppDatabase
import com.recapmaker.app.data.local.TokenManager
import com.recapmaker.app.data.local.VideoHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideTokenManager(@ApplicationContext ctx: Context): TokenManager {
        val tm = TokenManager(ctx)
        // Eagerly load the token cache on app startup, off the OkHttp interceptor thread.
        CoroutineScope(Dispatchers.IO).launch { tm.initCache() }
        return tm
    }

    @Provides @Singleton
    fun provideOkHttp(auth: AuthInterceptor): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(log)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // longer for video downloads
            .writeTimeout(120, TimeUnit.SECONDS)   // longer for video uploads
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        // BUG FIX #1: Lenient Gson so "5500Ks" (string in an Int field) doesn't crash
        // Also setLenient() handles any malformed JSON from server
        val gson = GsonBuilder()
            .setLenient()
            .create()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides @Singleton
    fun provideApi(retrofit: Retrofit): RecapApi = retrofit.create(RecapApi::class.java)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "recap_db").fallbackToDestructiveMigration().build()

    @Provides @Singleton
    fun provideHistoryDao(db: AppDatabase): VideoHistoryDao = db.videoHistoryDao()
}
