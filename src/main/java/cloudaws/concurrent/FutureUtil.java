package cloudaws.concurrent;

import cloudaws.Main;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FutureUtil {
	static final ScheduledExecutorService SERVICE = Main.PROMISE_POOL;
	static long ENQUEUE_DELAY = 1;

	static void enqueue(Runnable r) {
		enqueue(r, TimeUnit.MILLISECONDS);
	}

	static void enqueue(Runnable r, TimeUnit unit) {
		if (SERVICE != null) SERVICE.schedule(r, ENQUEUE_DELAY, unit);
	}
}
