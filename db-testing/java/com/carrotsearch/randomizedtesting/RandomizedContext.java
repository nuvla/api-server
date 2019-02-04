package com.carrotsearch.randomizedtesting;

import org.elasticsearch.common.Randomness;

import java.lang.Thread.State;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Random;
import java.util.WeakHashMap;



public final class RandomizedContext {
	/** Coordination at global level. */
	private static final Object _globalLock = new Object();
	/** Coordination at context level. */
	private        final Object _contextLock = new Object();

	/**
	 * Per thread assigned resources.
	 */
	private final static class PerThreadResources {
		/**
		 * Generators of pseudo-randomness. This is a queue because we stack
		 * them during lifecycle phases (suite/ method level).
		 */
		final ArrayDeque<Randomness> randomnesses = new ArrayDeque<Randomness>();
	}

	/**
	 * All thread groups we're currently tracking contexts for.
	 */
	final static IdentityHashMap<ThreadGroup, RandomizedContext> contexts
			= new IdentityHashMap<ThreadGroup, RandomizedContext>();

	/**
	 * Per thread resources for each context. Allow GCing of threads.
	 */
	final WeakHashMap<Thread, PerThreadResources> perThreadResources
			= new WeakHashMap<Thread, PerThreadResources>();



	/** The context and all of its resources are no longer usable. */
	private volatile boolean disposed;



	private Method currentMethod;

	public RandomizedContext(){

	}





	/** Runner's seed. */
	long getRunnerSeed() {
		return new java.util.Random().nextLong();
	}


	private final static char [] HEX = "0123456789ABCDEF".toCharArray();
	public static String formatSeed(long seed) {
		StringBuilder b = new StringBuilder();
		do {
			b.append(HEX[(int) (seed & 0xF)]);
			seed = seed >>> 4;
		} while (seed != 0);
		return b.reverse().toString();
	}

	/**
	 * Returns the runner's master seed, formatted.
	 */
	public String getRunnerSeedAsString() {
		checkDisposed();
		return formatSeed(getRunnerSeed());
	}

	/** Source of randomness for the context's thread. */
	public Randomness getRandomness() {
		return getPerThread().randomnesses.peekFirst();
	}

	/**
	 * Return all {@link Randomness} on the stack for the current thread. The most
	 * recent (currently used) randomness comes last in this array.
	 */
	Randomness [] getRandomnesses() {
		ArrayDeque<Randomness> randomnesses = getPerThread().randomnesses;
		Randomness[] array = randomnesses.toArray(new Randomness [randomnesses.size()]);
		for (int i = 0, j = array.length - 1; i < j; i++, j--) {
			Randomness r = array[i];
			array[i] = array[j];
			array[j] = r;
		}
		return array;
	}


	public Random getRandom() {
		return new java.util.Random();
	}



	/**
	 * @return Returns the context for the calling thread or throws an
	 *         {@link IllegalStateException} if the thread is out of scope.
	 * @throws IllegalStateException If context is not available.
	 */
	public static RandomizedContext current() {
		return new RandomizedContext();
	}




	/** Push a new randomness on top of the stack. */
	void push(Randomness rnd) {
		getPerThread().randomnesses.push(rnd);
	}

	/** Pop a randomness off the stack and dispose it. */
	void popAndDestroy() {
		//getPerThread().randomnesses.pop().destroy();
	}

	/** Return per-thread resources associated with the current thread. */
	private PerThreadResources getPerThread() {
		checkDisposed();
		synchronized (_contextLock) {
			return perThreadResources.get(Thread.currentThread());
		}
	}

	/**
	 * Throw an exception if disposed.
	 */
	private void checkDisposed() {
		if (disposed) {
			throw new IllegalStateException("Context disposed: " +
					toString() + " for thread: " + Thread.currentThread());
		}
	}

	/**
	 * Clone context information between the current thread and another thread.
	 * This is for internal use only to propagate context information when forking.
	 */
	static void cloneFor(Thread t) {
		if (t.getState() != State.NEW) {
			throw new IllegalStateException("The thread to share context with is not in NEW state: " + t);
		}

		final ThreadGroup tGroup = t.getThreadGroup();
		if (tGroup == null) {
			throw new IllegalStateException("No thread group for thread: " + t);
		}

		Thread me = Thread.currentThread();
		if (me.getThreadGroup() != tGroup) {
			throw new IllegalArgumentException("Both threads must share the thread group.");
		}

		synchronized (_globalLock) {
			RandomizedContext context = contexts.get(tGroup);
			if (context == null) {
				throw new IllegalStateException("No context information for thread: " + t);
			}

			synchronized (context._contextLock) {
				if (context.perThreadResources.containsKey(t)) {
					throw new IllegalStateException("Context already initialized for thread: " + t);
				}

				if (!context.perThreadResources.containsKey(me)) {
					throw new IllegalStateException("Context not initialized for thread: " + me);
				}

				PerThreadResources perThreadResources = new PerThreadResources();

				context.perThreadResources.put(t, perThreadResources);
			}
		}
	}

	void setTargetMethod(Method method) {
		this.currentMethod = method;
	}

	/**
	 * @return Return the currently executing test case method (the thread may still
	 * be within test rules and may never actually hit the method). This method may return
	 * <code>null</code> if called from the static context (no test case is being executed at
	 * the moment).
	 */
	public Method getTargetMethod() {
		checkDisposed();
		return currentMethod;
	}
}