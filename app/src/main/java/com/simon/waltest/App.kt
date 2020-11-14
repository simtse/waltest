package com.simon.waltest

import android.app.Application
import com.simon.waltest.IoScheduler.fixedIoRx3
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber

class App : Application() {

  override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }

    RxJavaPlugins.setIoSchedulerHandler { fixedIoRx3 }
  }
}

