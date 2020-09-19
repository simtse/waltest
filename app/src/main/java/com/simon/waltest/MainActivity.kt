package com.simon.waltest

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import java.text.DateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

  private val compositeDisposable = CompositeDisposable()

  private lateinit var _binding: ActivityMainBinding
  private val binding get() = _binding

  private val dbHelper by lazy {
    TestDatabaseOpenHelperCallback.createSupportSqlOpenHelper(this)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    _binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)

    binding.fab.setOnClickListener {
      testWriteAndReadFromDifferentWritableDatabase()
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

  private fun testWriteAndReadFromDifferentWritableDatabase() {
    compositeDisposable.add(
      Completable.fromCallable {
        val db = dbHelper.writableDatabase
        db.beginTransactionNonExclusive()

        insertValue(db, currentTimeString())

        Thread.sleep(3000)

        db.setTransactionSuccessful()
        db.endTransaction()
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { Snackbar.make(binding.root, "Done Write", Snackbar.LENGTH_SHORT).show() },
          { Log.e(null, "Errored for writing", it) }
        )
    )

    compositeDisposable.add(
      Single.fromCallable {
        val db = dbHelper.writableDatabase
        db.beginTransactionNonExclusive()
        val text = readValue(db)
        db.setTransactionSuccessful()
        db.endTransaction()

        text
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { Snackbar.make(binding.root, "Done Read - $it", Snackbar.LENGTH_SHORT).show() },
          { Log.e(null, "Errored for writing", it) }
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
