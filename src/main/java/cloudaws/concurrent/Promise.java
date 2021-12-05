package cloudaws.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

public class Promise<T> extends CompletableFuture<T> {
	private final Future<T> future;

	private final long enqueued;
	private final long timeout;

	public Promise(Future<T> future) {
		this(future, 10000);
	}

	public Promise(Future<T> future, long timeout) {
		this.future = future;
		this.timeout = Math.max(1, timeout);
		this.enqueued = System.currentTimeMillis();

		FutureUtil.enqueue(this::loop);
	}

	private void loop() {
		if (future.isDone()) {
			try {
				complete(future.get());
			} catch (InterruptedException e) {
				completeExceptionally(e);

			} catch (ExecutionException e) {
				completeExceptionally(e.getCause());
			}
		}
		else if (future.isCancelled()) {
			cancel(true);
		}
		else {
			if (System.currentTimeMillis() - enqueued < timeout) {
				FutureUtil.enqueue(this::loop);
			}
			else {
				completeExceptionally(new TimeoutException("Future timed out: " + future));
			}
		}
	}
}
