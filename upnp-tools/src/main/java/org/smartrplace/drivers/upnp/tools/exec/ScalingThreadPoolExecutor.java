package org.smartrplace.drivers.upnp.tools.exec;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ThreadPoolExecutor} that actively increases the number of threads when new tasks come in,
 * instead of waiting for the task queue to fill up. 
 * 
 * @see https://github.com/kimchy/kimchy.github.com/blob/master/_posts/2008-11-23-juc-executorservice-gotcha.textile
 */
public class ScalingThreadPoolExecutor extends ThreadPoolExecutor {

	/**
	 * number of threads that are actively executing tasks
	 */
	private final AtomicInteger activeCount = new AtomicInteger();
	
	public ScalingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,	BlockingQueue<Runnable> workQueue) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
	}

	public ScalingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,	BlockingQueue<Runnable> workQueue, ThreadFactory factory) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, factory);
	}

	public static ExecutorService newScalingThreadPool(int min, int max, long keepAliveTime) {
		ScalingQueue<Runnable> queue = new ScalingQueue<>();
		ThreadPoolExecutor executor = new ScalingThreadPoolExecutor(min, max, keepAliveTime, TimeUnit.MILLISECONDS, queue);
		executor.setRejectedExecutionHandler(new ForceQueuePolicy());
		queue.setThreadPoolExecutor(executor);
		return executor;
	}
	
	public static ExecutorService newScalingThreadPool(int min, int max, long keepAliveTime, ThreadFactory factory) {
		ScalingQueue<Runnable> queue = new ScalingQueue<>();
		ThreadPoolExecutor executor = new ScalingThreadPoolExecutor(min, max, keepAliveTime, TimeUnit.MILLISECONDS, queue, factory);
		executor.setRejectedExecutionHandler(new ForceQueuePolicy());
		queue.setThreadPoolExecutor(executor);
		return executor;
	}

	@Override
	public int getActiveCount() {
		return activeCount.get();
	}

	@Override
	protected void beforeExecute(Thread t, Runnable r) {
		activeCount.incrementAndGet();
	}

	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		activeCount.decrementAndGet();
	}
	
	private static final class ForceQueuePolicy implements RejectedExecutionHandler {
	    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
	        try {
	            executor.getQueue().put(r);
	        } catch (InterruptedException e) {
	            //should never happen since we never wait
	            throw new RejectedExecutionException(e);
	        }
	    }
	}
	
	private static final class ScalingQueue<E> extends LinkedBlockingQueue<E> {

		private static final long serialVersionUID = 1L;
		
		/**
		 * The executor this Queue belongs to
	     */
	    private ThreadPoolExecutor executor;

	    /**
	     * 	Creates a TaskQueue with a capacity of {@link Integer#MAX_VALUE}.
	     */
	    public ScalingQueue() {
	        super();
	    }

	    /**
		 * Creates a TaskQueue with the given (fixed) capacity.
	     * @param capacity the capacity of this queue.
	     */
//	    public ScalingQueue(int capacity) {
//	        super(capacity);
//	    }

	    /**
	     * Sets the executor this queue belongs to.
	     */
	    public void setThreadPoolExecutor(ThreadPoolExecutor executor) {
	        this.executor = executor;
	    }

	    /**
	     * Inserts the specified element at the tail of this queue if there is at
	     * least one available thread to run the current task. If all pool threads
	     * are actively busy, it rejects the offer.
	     *
	     * @param o the element to add.
	     * @return true if it was possible to add the element to this
	     * queue, else false
	     * @see ThreadPoolExecutor#execute(Runnable)
	     */
	    @Override
	    public boolean offer(E o) {
	        int allWorkingThreads = executor.getActiveCount() + super.size();
	        return allWorkingThreads < executor.getPoolSize() && super.offer(o);
	    }
	}
	
	
}
