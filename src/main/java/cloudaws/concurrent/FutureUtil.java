package cloudaws.concurrent;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FutureUtil {
	static ScheduledExecutorService SERVICE;
	static long ENQUEUE_DELAY = 1;

	static void enqueue(Runnable r) {
		enqueue(r, TimeUnit.MILLISECONDS);
	}

	static void enqueue(Runnable r, TimeUnit unit) {
		if (SERVICE != null) SERVICE.schedule(r, ENQUEUE_DELAY, unit);
	}

	public static void provide(ScheduledExecutorService service, long delay) {
		ENQUEUE_DELAY = Math.max(1, delay);
		if (service != null) SERVICE = service;
		else {
			throw new NullPointerException("The providing service cannot be null.");
		}
	}

	public static void terminate() {
		if (SERVICE != null) SERVICE.shutdown();
	}
}
