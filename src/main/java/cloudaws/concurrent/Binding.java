package cloudaws.concurrent;

import cloudaws.Main;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Binding<T> {
	private static final ScheduledExecutorService SERVICE = Main.BINDING_POOL;
	private static final long DEFAULT_RELOAD_DELAY = 1000;

	private T data;
	private T defaultValue = null;

	private Supplier<CompletableFuture<T>> provider;
	private Predicate<Throwable> failure;

	private final Map<Object, Consumer<T>> notifiers = new HashMap<>();
	private final Set<Object> active = new HashSet<>();

	private long period;
	private boolean stopped = false;

	public Binding(Supplier<CompletableFuture<T>> provider) {
		this(provider, null, DEFAULT_RELOAD_DELAY);
	}

	public Binding(Supplier<CompletableFuture<T>> provider, Predicate<Throwable> failure) {
		this(provider, failure, DEFAULT_RELOAD_DELAY);
	}

	public Binding(Supplier<CompletableFuture<T>> provider, long period) {
		this(provider, null, period);
	}

	public Binding(Supplier<CompletableFuture<T>> provider, Predicate<Throwable> failure, long period) {
		this.provider = Objects.requireNonNull(provider);
		this.failure = failure;
		this.period = Math.max(1, period);
	}

	public synchronized Binding<T> withProvider(Supplier<CompletableFuture<T>> provider) {
		this.provider = Objects.requireNonNull(provider);
		return this;
	}

	public synchronized Binding<T> onFailure(Predicate<Throwable> failure) {
		this.failure = failure;
		return this;
	}

	public synchronized Binding<T> withNotifier(Object obj, Consumer<T> notify) {
		notifiers.put(obj, notify);
		active.add(obj);

		return this;
	}

	public Binding<T> withDefault(T defaultValue) {
		this.defaultValue = defaultValue;
		return this;
	}

	public Binding<T> withPeriod(long period) {
		this.period = period;
		return this;
	}

	public synchronized void bind(Object obj, Consumer<T> notify) {
		withNotifier(obj, notify);
	}

	public synchronized void listen(Object obj) {
		if (notifiers.containsKey(obj)) active.add(obj);
	}

	public synchronized void resume(Object obj) {
		this.listen(obj);
	}

	public synchronized void pause(Object obj) {
		active.remove(obj);
	}

	public synchronized void unbind(Object obj) {
		active.remove(obj);
		notifiers.remove(obj);
	}

	public synchronized void clear() {
		active.clear();
		notifiers.clear();
	}

	public T get() {
		return this.data;
	}

	public void start() {
		stopped = false;
		SERVICE.schedule(this::update, 0, TimeUnit.MILLISECONDS);
	}

	protected void update() {
		long start = System.currentTimeMillis();
		if (!active.isEmpty()) {
			provider.get().thenAccept(newData -> {
				this.data = newData != null ? newData : this.defaultValue;
				long elapsed = System.currentTimeMillis() - start;

				for (Object obj : active) {
					notifiers.get(obj).accept(this.data);
				}

				if (!stopped) SERVICE.schedule(this::update, Math.min(this.period, Math.max(1, this.period - elapsed)), TimeUnit.MILLISECONDS);
			}).exceptionally(exception -> {
				this.data = defaultValue;
				boolean ignore = true;
				if (this.failure != null) ignore = this.failure.test(exception);

				if (!stopped && ignore) {
					SERVICE.schedule(this::update, this.period, TimeUnit.MILLISECONDS);
				}
				return null;
			});
		}
		else if (!stopped) {
			SERVICE.schedule(this::update, this.period, TimeUnit.MILLISECONDS);
		}
	}

	public void stop() {
		stopped = true;
	}
}
