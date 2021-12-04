package cloudaws;

import cloudaws.concurrent.FutureUtil;
import cloudaws.ec2.EC2Manager;
import cloudaws.ui.MainScreen;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {
	private static boolean terminated = false;
	private static final Log logger = LogFactory.getLog(Main.class);

	public static final ScheduledExecutorService SERVICE = Executors.newSingleThreadScheduledExecutor();
	public static final EC2Manager EC2 = EC2Manager.INSTANCE;

	private static MainScreen screen;

	public static void initialize() {
		FutureUtil.provide(SERVICE, 1);
		EC2.init();
		Runtime.getRuntime().addShutdownHook(new Thread(Main::terminate, "TERM_HOOK"));
		terminated = false;

		screen = new MainScreen();
	}

	public static void terminate() {
		if (!terminated) {
			EC2.terminate();
			FutureUtil.terminate();

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
