package com.simon.waltest

import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * An Rx scheduler backed by a fixed sie thread pool. Intended for IO work.
 */
@Suppress("MagicNumber")
object IoScheduler {

  private val threadPool: ThreadPoolExecutor by lazy {
    ThreadPoolExecutor(
      4,
      4,
      1_000L,
      TimeUnit.MILLISECONDS,
      LinkedBlockingQueue<Runnable>(),
      object : ThreadFactory {
        private val counter = AtomicLong()
        override fun newThread(r: Runnable): Thread {
          val thread = Thread(r, "fixed-io-" + counter.incrementAndGet())
          thread.isDaemon = true
          return thread
        }
      }
    )
  }

  @JvmStatic
  val fixedIoRx3: Scheduler by lazy {
    Schedulers.from(threadPool)
  }
}
