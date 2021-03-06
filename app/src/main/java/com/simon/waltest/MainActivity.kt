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

    binding.mainContent.navRxTest.setOnClickListener {
      startActivity(RxJavaDeadlockActivity.newIntent(this))
    }

    binding.mainContent.diffDbWTrans.setOnClickListener {
      testWriteAndReadFromDifferentWritableDatabase(
        DELAY_DB_WRITE_MS,
        dbHelper.writableDatabase,
        dbHelper.readableDatabase
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

    binding.mainContent.delayDoubleReadWithTransaction.setOnClickListener {
      readWithDelay(1, DELAY_DB_WRITE_MS, dbHelper.writableDatabase, true)
      readWithDelay(2, 0, dbHelper.writableDatabase, true)
    }

    binding.mainContent.delayDoubleReadWithoutTransaction.setOnClickListener {
      readWithDelay(1, DELAY_DB_WRITE_MS, dbHelper.writableDatabase, true)
      readWithDelay(2, 0, dbHelper.writableDatabase, false)
    }

    binding.mainContent.delayDoubleWriteWithTransaction.setOnClickListener {
      writeWithDelay(1, DELAY_DB_WRITE_MS, dbHelper.writableDatabase, true)
      writeWithDelay(2, 0, dbHelper.writableDatabase, true)
    }

    binding.mainContent.delayDoubleWriteWithoutTransaction.setOnClickListener {
      writeWithDelay(1, DELAY_DB_WRITE_MS, dbHelper.writableDatabase, true)
      writeWithDelay(2, 0, dbHelper.writableDatabase, false)
      writeWithDelay(3, 0, dbHelper.writableDatabase, false)
      writeWithDelay(4, 0, dbHelper.writableDatabase, false)
      writeWithDelay(5, 0, dbHelper.writableDatabase, false)
    }

    binding.mainContent.nestedInsertRead.setOnClickListener {
      tryToDeadlockTransactions(dbHelper.writableDatabase)
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

        db.beginTransactionNonExclusive()
        Timber.d("$threadName write started transaction")

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

          db.beginTransactionNonExclusive()
          Timber.d("$threadName read started transaction")
          val text = readValue(db)
          db.setTransactionSuccessful()
          db.endTransaction()

          Timber.d("$threadName read transaction done")
          text
        } else {
          Timber.d("$threadName read start - no transaction")
          val text = readValue(db)
          Timber.d("$threadName read done - no transaction")
          text
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

  private fun readWithDelay(
    requestNum: Int,
    delay: Long,
    db: SupportSQLiteDatabase,
    readShouldUseTransaction: Boolean = true
  ) {
    compositeDisposable.add(
      Single.fromCallable {
        val threadName = Thread.currentThread().name
        if (readShouldUseTransaction) {

          db.beginTransactionNonExclusive()
          Timber.d("$threadName #$requestNum read started transaction")

          if (delay > 0) {
            Timber.d("$threadName #$requestNum read going to sleep for $delay ms")
            Thread.sleep(delay)
          }

          val text = readValue(db)
          db.setTransactionSuccessful()
          db.endTransaction()

          Timber.d("$threadName #$requestNum read transaction done")
          text
        } else {
          Timber.d("$threadName #$requestNum read start - no transaction")
          val text = readValue(db)
          Timber.d("$threadName #$requestNum read done - no transaction")
          text
        }
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { Snackbar.make(rootView, "#$requestNum Done Read - $it", Snackbar.LENGTH_SHORT).show() },
          { Timber.e(it, "Errored for writing") }
        )
    )
  }

  private fun writeWithDelay(
    requestNum: Int,
    delay: Long,
    db: SupportSQLiteDatabase,
    readShouldUseTransaction: Boolean = true
  ) {
    compositeDisposable.add(
      Completable.fromCallable {
        val threadName = Thread.currentThread().name
        if (readShouldUseTransaction) {
          db.beginTransactionNonExclusive()
          Timber.d("$threadName #$requestNum write started transaction")

          insertValue(db, currentTimeString())

          if (delay > 0) {
            Timber.d("$threadName #$requestNum write going to sleep for $delay ms")
            Thread.sleep(delay)
          }

          db.setTransactionSuccessful()
          db.endTransaction()
          Timber.d("$threadName #$requestNum write transaction done")
        } else {
          Timber.d("$threadName #$requestNum write started no transaction")
          insertValue(db, currentTimeString())
          Timber.d("$threadName #$requestNum write ended no transaction")
        }
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { Snackbar.make(rootView, "Done Write", Snackbar.LENGTH_SHORT).show() },
          { Timber.e(it, "Errored for writing") }
        )
    )
  }

  private fun tryToDeadlockTransactions(db: SupportSQLiteDatabase) {

    val readSingle = Single.fromCallable {
      val threadName = Thread.currentThread().name
      Timber.d("$threadName read - about to start transaction")
      db.beginTransactionNonExclusive()
      Timber.d("$threadName read - transaction started")
      val text = readValue(db)
      Timber.d("$threadName read - value read")
      db.setTransactionSuccessful()
      db.endTransaction()
      Timber.d("$threadName read - transaction completed")
      text
    }

    val readSingleNoTransaction = Single.fromCallable {
      val threadName = Thread.currentThread().name
      Timber.d("$threadName read - no transaction started")
      val text = readValue(db)
      Timber.d("$threadName read - no transaction ended $text")
      text
    }

    Completable.fromCallable {
      val threadName = Thread.currentThread().name
      Timber.d("$threadName insert - about to start transaction")
      db.beginTransactionNonExclusive()
      Timber.d("$threadName insert - transaction started")

      insertValue(db, currentTimeString())
      Timber.d("$threadName insert - value inserted")
      val readResult = readSingle.subscribeOn(Schedulers.computation()).blockingGet()

      Timber.d("$threadName insert - back from blocking get and got value $readResult")

      db.setTransactionSuccessful()
      db.endTransaction()
      Timber.d("$threadName insert - transaction all ended")
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { Timber.d("Done inserting (with read in between)") },
        { Timber.e(it, "Error with nested transaction") }
      )

    Single.fromCallable {
      val threadName = Thread.currentThread().name
      Timber.d("$threadName secondary sleep 1s")
      Thread.sleep(1000)
      Timber.d("$threadName secondary start blocking get")
      val result = readSingleNoTransaction.blockingGet()
      Timber.d("$threadName secondary end blocking get $result")
      result
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
      { Timber.d("Done fetching secondary $it") },
      { Timber.e(it, "Error fetching secondary") }
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
      "SELECT _id, value FROM $TABLE_NAME_TEST ORDER BY _id DESC LIMIT 1"
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
