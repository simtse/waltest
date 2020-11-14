package com.simon.waltest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.simon.waltest.databinding.ActivityRxjavaDeadlockBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class RxJavaDeadlockActivity : AppCompatActivity() {

  private val compositeDisposable = CompositeDisposable()
  private lateinit var binding: ActivityRxjavaDeadlockBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityRxjavaDeadlockBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)

    setUpButtons()
  }

  companion object {
    fun newIntent(context: Context): Intent {
      return Intent(context, RxJavaDeadlockActivity::class.java)
    }
  }

  private fun setUpButtons() {
    binding.rxSubscribe.setOnClickListener {
      val disposable = Single.fromCallable {
        "Initial String for subscribe"
      }
        .flatMap {
          Single.fromCallable { "Inner subscribe" }
            .subscribeOn(Schedulers.single())
        }
        .subscribeOn(Schedulers.single())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          {
            Snackbar.make(binding.root, "Done action - $it", Snackbar.LENGTH_SHORT).show()
          },
          {
            Snackbar.make(binding.root, "Error - inner subscribe!!", Snackbar.LENGTH_SHORT).show()
          }
        )

      compositeDisposable.add(disposable)
    }

    binding.rxBlockingGet.setOnClickListener {
      val result = try {
        Single.fromCallable {
          "Initial String for blocking get"
        }
          .flatMap {
            Single.fromCallable { "Inner Blocking" }
              .subscribeOn(Schedulers.single())
          }
          .subscribeOn(Schedulers.single())
          .blockingGet()
      } catch (e: Exception) {
        "Error value"
      }

      Snackbar.make(binding.root, "Done action - $result", Snackbar.LENGTH_SHORT).show()
    }

    binding.resetRxActions.setOnClickListener {
      compositeDisposable.clear()
    }
  }

}