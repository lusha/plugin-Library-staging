/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.util.concurrent;

import plugins.Library.util.func.Closure;
import plugins.Library.util.func.SafeClosure;
import plugins.Library.util.func.Tuples.*;
import static plugins.Library.util.func.Tuples.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
** A class that wraps around an {@link Executor}, for processing any given type
** of object, not just {@link Runnable}. Each object must be accompanied by a
** secondary "deposit" object, which is returned with the object when it has
** been processed. Any exceptions thrown are also returned.
**
** @param <T> Type of object to be processed
** @param <E> Type of object to be used as a deposit
** @param <X> Type of exception thrown by {@link #clo}
** @author infinity0
*/
public class ObjectProcessor<T, E, X extends Exception> implements Scheduler {

	final protected BlockingQueue<T> in;
	final protected BlockingQueue<$2<T, X>> out;
	final protected Map<T, E> dep;
	final protected Closure<T, X> clo;
	final protected Executor exec;

	protected Thread auto = null;
	protected boolean open = true;

	/**
	** Constructs a new processor. The processor itself will be thread-safe
	** as long as the queues and deposit map are not exposed to other threads,
	** and the closure's invoke method is also thread-safe.
	**
	** @param i Queue for input items
	** @param o Queue for output/error items
	** @param d Map for item deposits
	** @param c Closure to call on each item
	** @param x Executor to run each closure call
	*/
	public ObjectProcessor(BlockingQueue<T> i, BlockingQueue<$2<T, X>> o, Map<T, E> d, Closure<T, X> c, Executor x) {
		in = i;
		out = o;
		dep = d;
		clo = c;
		exec = x;
	}

	/**
	** Submits an item for processing, with the given deposit.
	**
	** @throws IllegalStateException if the processor has already been {@link
	**         #close() closed}
	** @throws IllegalArgumentException if the item is already being held
	*/
	public synchronized void submit(T item, E deposit) throws InterruptedException {
		if (!open) { throw new IllegalStateException("ObjectProcessor: not open"); }
		if (dep.containsKey(item)) {
			throw new IllegalArgumentException("ObjectProcessor: object " + item + " already submitted");
		}

		dep.put(item, deposit);
		in.put(item);
	}

	/**
	** Updates the deposit for a given item.
	**
	** @throws IllegalStateException if the processor has already been {@link
	**         #close() closed}
	** @throws IllegalArgumentException if the item is not currently being held
	*/
	public synchronized void update(T item, E deposit) {
		if (!open) { throw new IllegalStateException("ObjectProcessor: not open"); }
		if (!dep.containsKey(item)) {
			throw new IllegalArgumentException("ObjectProcessor: object " + item + " not yet submitted");
		}

		dep.put(item, deposit);
	}

	/**
	** Retrieved a processed item, along with its deposit and any exception
	** that caused processing to abort.
	*/
	public synchronized $3<T, E, X> accept() throws InterruptedException {
		$2<T, X> item = out.take();
		return $3(item._0, dep.remove(item._0), item._1);
	}

	/**
	** Whether there are any unprocessed items.
	*/
	public synchronized boolean hasPending() {
		return !dep.isEmpty();
	}

	/**
	** Whether there are any completed items that have not yet been retrieved.
	*/
	public synchronized boolean hasCompleted() {
		return !out.isEmpty();
	}

	/**
	** Creates a {@link Runnable} for processing a submitted item and sends it
	** to {@link #exec} for execution. This method is provided for completeness
	** in case anyone needs it; {@link #auto(SafeClosure)} should be adequate
	** for most purposes.
	*/
	public void handle() throws InterruptedException {
		final T item = in.take();
		exec.execute(new Runnable() {
			/*@Override**/ public void run() {
				X ex = null;
				try { clo.invoke(item); }
				catch (Exception e) { ex = (X)e; }
				try { out.put($2(item, ex)); }
				catch (InterruptedException e) { throw new UnsupportedOperationException(); }
			}
		});
	}

	/**
	** Starts a new {@link Thread} which waits on the input queue and calls
	** {@link #handle()} for each incoming item.
	**
	** @param shutdown If not {@code null}, this is called on {@link #exec}
	**        when the thread completes.
	** @throws IllegalThreadStateException if a thread was already started
	*/
	public synchronized void auto(final SafeClosure<Executor> shutdown) {
		if (auto == null) {
			auto = new Thread() {
				@Override public void run() {
					try {
						while (open || !in.isEmpty()) {
							try {
								handle();
							} catch (InterruptedException e) {
								continue;
							}
						}
					} finally {
						if (shutdown != null) { shutdown.invoke(exec); }
					}
				}
			};
		}
		auto.start();
	}

	/**
	** @see #auto(SafeClosure)
	*/
	public void auto() {
		auto(null);
	}

	/**
	** Stop accepting {@linkplain #submit(Object, Object) new submissions} or
	** {@linkplain #update(Object, Object) updates}. Held items can still be
	** {@linkplain #handle() handled} and {@linkplain #accept() retrieved}, and
	** if an {@linkplain #auto(SafeClosure) auto-handler} is running, it will
	** run until all items have been processed.
	*/
	/*@Override**/ public synchronized void close() {
		open = false;
		if (auto != null) { auto.interrupt(); }
	}

	// public class Object

	/**
	** This implementation just calls {@link #close()}.
	*/
	@Override public void finalize() {
		close();
	}

}