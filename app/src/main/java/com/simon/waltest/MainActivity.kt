package com.simon.waltest

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.material.snackbar.Snackbar
import com.simon.waltest.TestDatabaseOpenHelperCallback.Companion.TABLE_NAME_TEST
import com.simon.waltest.databinding.ActivityMainBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.text.DateFormat
import java.util.*

private const val DELAY_DB_WRITE_MS = 3000L

class MainActivity : AppCompatActivity() {

  private val compositeDisposable = CompositeDisposable()

  private lateinit var _binding: ActivityMainBinding
  private val binding get() = _binding

  private val dbHelper by lazy {
    TestDatabaseOpenHelperCallback.createSupportSqlOpenHelper(this)
  }

  private val rootView by lazy {
    findViewById<View>(R.id.root)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    _binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)

    binding.mainContent.diffDbWTrans.setOnClickListener {
      testWriteAndReadFromDifferentWritableDatabase(
        DELAY_DB_WRITE_MS,
        dbHelper.writableDatabase,
        dbHelper.writableDatabase
      )
    }

    binding.mainContent.sameDbWTrans.setOnClickListener {
      val db = dbHelper.writableDatabase
      testWriteAndReadFromDifferentWritableDatabase(
        DELAY_DB_WRITE_MS,
        db,
        db
      )
    }

    binding.mainContent.diffDbWithoutTrans.setOnClickListener {
      testWriteAndReadFromDifferentWritableDatabase(
        DELAY_DB_WRITE_MS,
        dbHelper.writableDatabase,
        dbHelper.writableDatabase,
        false
      )
    }

    binding.mainContent.sameDbWithoutTrans.setOnClickListener {
      val db = dbHelper.writableDatabase
      testWriteAndReadFromDifferentWritableDatabase(
        DELAY_DB_WRITE_MS,
        db,
        db,
        false
      )
    }

    binding.mainContent.noDelayWithTransaction.setOnClickListener {
      testWriteAndReadFromDifferentWritableDatabase(
        0,
        dbHelper.writableDatabase,
        dbHelper.writableDatabase,
        true
      )
    }

    binding.mainContent.noDelayWithoutTransaction.setOnClickListener {
      testWriteAndReadFromDifferentWritableDatabase(
        0,
        dbHelper.writableDatabase,
        dbHelper.writableDatabase,
        false
      )
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    // Inflate the menu; this adds items to the action bar if it is present.
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    return when (item.itemId) {
      R.id.action_settings -> true
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun testWriteAndReadFromDifferentWritableDatabase(
    delay: Long,
    writingDatabase: SupportSQLiteDatabase,
    readingDatabase: SupportSQLiteDatabase,
    readShouldUseTransaction: Boolean = true
  ) {
    compositeDisposable.add(
      Completable.fromCallable {
        val threadName = Thread.currentThread().name
        val db = writingDatabase
        Timber.d("$threadName write about to start transaction")

        db.beginTransactionNonExclusive()

        insertValue(db, currentTimeString())

        if (delay > 0) {
          Timber.d("$threadName write going to sleep for $delay ms")
          Thread.sleep(delay)
        }

        db.setTransactionSuccessful()
        db.endTransaction()
        Timber.d("$threadName write transaction done")
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { Snackbar.make(rootView, "Done Write", Snackbar.LENGTH_SHORT).show() },
          { Timber.e(it, "Errored for writing") }
        )
    )

    compositeDisposable.add(
      Single.fromCallable {
        val threadName = Thread.currentThread().name
        val db = readingDatabase

        if (readShouldUseTransaction) {
          Timber.d("$threadName read about to start transaction")

          db.beginTransactionNonExclusive()
          val text = readValue(db)
          db.setTransactionSuccessful()
          db.endTransaction()

          Timber.d("$threadName read transaction done")
          text
        } else {
          Timber.d("$threadName read start - no transaction")
          readValue(db)
          Timber.d("$threadName read done - no transaction")
        }
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { Snackbar.make(rootView, "Done Read - $it", Snackbar.LENGTH_SHORT).show() },
          { Timber.e(it, "Errored for writing") }
        )
    )
  }

  override fun onStop() {
    compositeDisposable.clear()
    super.onStop()
  }

  private fun insertValue(supportSQLiteDatabase: SupportSQLiteDatabase, text: String) {
    val contentValues = ContentValues()
    contentValues.put("value", text)
    supportSQLiteDatabase.insert(TABLE_NAME_TEST, SQLiteDatabase.CONFLICT_NONE, contentValues)
  }

  private fun readValue(supportSQLiteDatabase: SupportSQLiteDatabase): String {
    val cursor: Cursor = supportSQLiteDatabase.query(
      "SELECT _id, value FROM $TABLE_NAME_TEST LIMIT 1"
    )
    return cursor.use {
      it.moveToFirst()
      cursor.getString(cursor.getColumnIndex("value"))
    }
  }

  private fun currentTimeString(): String {
    val dateFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, Locale.ENGLISH)
    val today = Date()
    return dateFormatter.format(today)
  }
}
