package cloudaws;

import cloudaws.concurrent.FutureUtil;
import cloudaws.ec2.EC2Manager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {
	private static final ScheduledExecutorService PROMISE_POOL = Executors.newSingleThreadScheduledExecutor();
	private static final EC2Manager EC2 = EC2Manager.INSTANCE;

	public static void initialize() {
		FutureUtil.provide(PROMISE_POOL, 1);
		EC2.init();
	}

	public static void terminate() {
		EC2.terminate();
		FutureUtil.terminate();
	}

	public static void main(String[] args) {
		initialize();

		EC2.getInstances()
			.thenAccept(result -> {
				result.getReservations().forEach(rsv -> rsv.getInstances().forEach(ins -> System.out.println(ins.getInstanceId())));
			})
			.thenAccept(_void -> {
				terminate();
			});
	}
}
