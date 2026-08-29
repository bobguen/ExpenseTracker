package com.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.expensetracker.data.local.AppDatabase
import com.expensetracker.data.remote.exchange.ExchangeRateService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase {
        val passphrase = SQLiteDatabase.getBytes("expense_tracker_passphrase".toCharArray()) // In prod, use AndroidKeystore
        val factory = SupportFactory(passphrase)
        return Room.databaseBuilder(ctx, AppDatabase::class.java, "expenses.db")
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideTxDao(db: AppDatabase) = db.transactionDao()
    @Provides fun provideRateDao(db: AppDatabase) = db.rateCacheDao()

    @Provides @Singleton
    fun provideRateService(): ExchangeRateService {
        return Retrofit.Builder()
            .baseUrl("https://api.exchangerate.host/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(ExchangeRateService::class.java)
    }

    @Provides @Singleton
    fun provideAlarmManager(@ApplicationContext ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
}
