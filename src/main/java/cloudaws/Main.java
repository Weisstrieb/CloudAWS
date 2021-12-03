package cloudaws;

import cloudaws.concurrent.FutureUtil;
import cloudaws.ec2.EC2Manager;
import cloudaws.ui.MainScreen;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {
	private static final Log logger = LogFactory.getLog(Main.class);

	private static final ScheduledExecutorService SERVICE = Executors.newSingleThreadScheduledExecutor();
	private static final EC2Manager EC2 = EC2Manager.INSTANCE;

	private static MainScreen screen;

	public static void initialize() {
		FutureUtil.provide(SERVICE, 1);
		EC2.init();
	}

	public static void terminate() {
		EC2.terminate();
		FutureUtil.terminate();
	}

	public static boolean isQuit(KeyStroke stroke) {
		return stroke != null &&
				(stroke.getKeyType() == KeyType.Escape || (stroke.isCtrlDown() && stroke.getCharacter() == 'c'));
	}

	public static void main(String[] args) {
		screen = new MainScreen();

		screen.loadMenu();

		screen.collapse();
	}
}
