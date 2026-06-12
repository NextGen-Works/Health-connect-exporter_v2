package com.healthconnect.exporter.di

import android.content.Context
import androidx.room.Room
import com.healthconnect.exporter.core.Logger
import com.healthconnect.exporter.network.HcgatewayApi
import com.healthconnect.exporter.storage.AppDatabase
import com.healthconnect.exporter.storage.AppEventLogDao
import com.healthconnect.exporter.storage.NormalizedHealthRecordDao
import com.healthconnect.exporter.storage.PermissionStateDao
import com.healthconnect.exporter.storage.RawHealthRecordDao
import com.healthconnect.exporter.storage.SyncCursorDao
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://placeholder.example.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideHcgatewayApi(retrofit: Retrofit): HcgatewayApi {
        return retrofit.create(HcgatewayApi::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "health_exporter.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRawHealthRecordDao(db: AppDatabase): RawHealthRecordDao = db.rawHealthRecordDao()

    @Provides
    fun provideNormalizedHealthRecordDao(db: AppDatabase): NormalizedHealthRecordDao = db.normalizedHealthRecordDao()

    @Provides
    fun provideSyncCursorDao(db: AppDatabase): SyncCursorDao = db.syncCursorDao()

    @Provides
    fun provideEventLogDao(db: AppDatabase): AppEventLogDao = db.eventLogDao()

    @Provides
    fun providePermissionStateDao(db: AppDatabase): PermissionStateDao = db.permissionStateDao()

    @Provides
    @Singleton
    fun provideLogger(@ApplicationContext context: Context, database: AppDatabase): Logger {
        return Logger(context, database)
    }
}
