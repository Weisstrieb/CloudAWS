package cloudaws;

import cloudaws.concurrent.FutureUtil;
import cloudaws.ec2.EC2Manager;
import cloudaws.ui.MainScreen;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {
	private static boolean terminated = false;

	public static final ScheduledExecutorService PROMISE_POOL = Executors.newSingleThreadScheduledExecutor();
	public static final ScheduledExecutorService BINDING_POOL = Executors.newSingleThreadScheduledExecutor();

	public static final EC2Manager EC2 = EC2Manager.INSTANCE;

	private static MainScreen screen;

	public static void initialize() {
		Runtime.getRuntime().addShutdownHook(new Thread(Main::terminate, "TERM_HOOK"));
		EC2.init();
		terminated = false;

		screen = new MainScreen();
	}

	public static void terminate() {
		if (!terminated) {
			EC2.terminate();
			PROMISE_POOL.shutdown();
			BINDING_POOL.shutdown();

			screen.collapse();
			terminated = true;
		}
	}

	public static void main(String[] args) {
		initialize();
		screen.show();
		terminate();
	}
}
