package com.simon.waltest

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

private const val DB_VERSION = 1
private const val TABLE_NAME_TEST = "test"
private const val DB_NAME = "wal_test"

class TestDatabaseOpenHelperCallback : SupportSQLiteOpenHelper.Callback(DB_VERSION) {

  override fun onConfigure(db: SupportSQLiteDatabase) {
    db.enableWriteAheadLogging()
  }

  override fun onCreate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE $TABLE_NAME_TEST (_id integer primary key autoincrement, value text)")
  }

  override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // nothing
  }

  companion object {
    fun createSupportSqlOpenHelper(context: Context): SupportSQLiteOpenHelper {
      val config = SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(DB_NAME)
        .callback(TestDatabaseOpenHelperCallback())
        .build()

      return FrameworkSQLiteOpenHelperFactory().create(config)
    }
  }
}