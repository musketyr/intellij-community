package com.intellij.execution.process;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread pool for long-running workers needed for handling child processes, to avoid occupying workers in the main application pool
 * and constantly creating new threads there.
 *
 * @since 2016.2
 * @author peter
 */
public class ProcessIOExecutorService extends ThreadPoolExecutor {
  public static final String POOLED_THREAD_PREFIX = "Process I/O pool ";
  public static final ProcessIOExecutorService INSTANCE = new ProcessIOExecutorService();

  private ProcessIOExecutorService() {
    super(1, Integer.MAX_VALUE, 1, TimeUnit.MINUTES, new SynchronousQueue<Runnable>(), new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger();
      @NotNull
      @Override
      public Thread newThread(@NotNull final Runnable r) {
        Thread thread = new Thread(r, POOLED_THREAD_PREFIX + counter.incrementAndGet());
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
      }
    });
  }
}
