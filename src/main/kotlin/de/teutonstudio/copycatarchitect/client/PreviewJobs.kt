package de.teutonstudio.copycatarchitect.client

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Screen-owned results for the current revision. Only immutable inputs go to the shared worker. */
internal class PreviewJobs<K,V> {
    private val tasks=mutableMapOf<K,FutureTask<Result<V>>>()

    fun request(key:K,compute:()->V):Result<V>? {
        tasks[key]?.let { return if(it.isDone) it.get() else null }
        val task=FutureTask { runCatching(compute) }
        try {
            worker.execute(task)
            tasks[key]=task
        } catch(_:RejectedExecutionException) {
            // A visible preview retries on the next frame if the queue is full.
        }
        return null
    }

    fun cancelPending() {
        tasks.values.removeIf { task ->
            if(task.isDone) return@removeIf false
            // A running evaluator can finish safely, but its result is no longer observable.
            task.cancel(false)
            worker.remove(task)
            true
        }
    }

    fun clear() {
        cancelPending()
        tasks.clear()
    }

    private companion object {
        val worker=ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue(16)) { action ->
            Thread(action,"Architect preview").apply { isDaemon=true }
        }.apply { allowCoreThreadTimeOut(true) }
    }
}
