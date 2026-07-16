package cn.jiayi.familymemory.di

import android.content.Context
import androidx.room.Room
import cn.jiayi.familymemory.BuildConfig
import cn.jiayi.familymemory.data.connection.AccessTokenStore
import cn.jiayi.familymemory.data.connection.ConnectionPreferences
import cn.jiayi.familymemory.data.connection.DataStoreConnectionPreferences
import cn.jiayi.familymemory.security.KeystoreAccessTokenStore
import cn.jiayi.familymemory.data.local.CoreDao
import cn.jiayi.familymemory.data.local.FamilyDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    abstract fun bindConnectionPreferences(implementation: DataStoreConnectionPreferences): ConnectionPreferences

    @Binds
    abstract fun bindTokenStore(implementation: KeystoreAccessTokenStore): AccessTokenStore
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FamilyDatabase =
        Room.databaseBuilder(context, FamilyDatabase::class.java, "family-memory.db").build()

    @Provides
    fun provideCoreDao(database: FamilyDatabase): CoreDao = database.coreDao()
}
