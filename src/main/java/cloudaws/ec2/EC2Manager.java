package cloudaws.ec2;

import cloudaws.concurrent.Promise;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Async;
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder;
import com.amazonaws.services.ec2.model.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EC2Manager {

	public static final EC2Manager INSTANCE = new EC2Manager();
	private static final Log logger = LogFactory.getLog(EC2Manager.class);

	private AmazonEC2AsyncClientBuilder builder;
	private AmazonEC2Async client;

	private EC2Manager() {
		this(null,null);
	}

	private EC2Manager(String profile, Regions region) {
		builder = AmazonEC2AsyncClientBuilder.standard()
				.withCredentials(new ProfileCredentialsProvider(profile));
		if (region != null) {
			builder = builder.withRegion(region);
		}
	}

	public void init() {
		client = builder.build();
	}

	public boolean isInitialized() {
		return client != null;
	}

	private void assertInit() {
		if (!isInitialized()) {
			String msg = "EC2Manager was failed to initialize.";

			logger.fatal(msg);
			throw new RuntimeException("[FATAL] " + msg);
		}
	}

	// Instance management
	public CompletableFuture<Instance> createInstance(RunInstancesRequest req) {
		assertInit();
		return new Promise<>(client.runInstancesAsync(req)).thenApply(result -> result.getReservation().getInstances().get(0));
	}

	public CompletableFuture<List<Instance>> getInstances() {
		assertInit();
		DescribeInstancesRequest req = new DescribeInstancesRequest();
		return new Promise<>(client.describeInstancesAsync()).thenApply(result -> {
			List<Instance> list = new ArrayList<>();
			do {
				result.getReservations().forEach(rsv -> list.addAll(rsv.getInstances()));
				req.setNextToken(req.getNextToken());
			}
			while (req.getNextToken() != null);

			return list;
		});
	}

	public void startInstance(String instanceId) {
		assertInit();
		StartInstancesRequest req = new StartInstancesRequest().withInstanceIds(instanceId);
		client.startInstancesAsync(req);
	}

	public void stopInstance(String instanceId) {
		assertInit();
		StopInstancesRequest req = new StopInstancesRequest().withInstanceIds(instanceId);
		client.stopInstancesAsync(req);
	}

	public void rebootInstance(String instanceId) {
		assertInit();
		RebootInstancesRequest req = new RebootInstancesRequest().withInstanceIds(instanceId);
		client.rebootInstancesAsync(req);
	}

	// Zones & Regions
	public CompletableFuture<List<AvailabilityZone>> avaliableZones() {
		assertInit();
		return new Promise<>(client.describeAvailabilityZonesAsync()).thenApply(res -> res.getAvailabilityZones());
	}

	public CompletableFuture<List<Region>> availableRegions() {
		assertInit();
		return new Promise<>(client.describeRegionsAsync()).thenApply(res -> res.getRegions());
	}

	// Image management
	public CompletableFuture<List<Image>> getImages() {
		assertInit();
		return new Promise<>(client.describeImagesAsync()).thenApply(res -> res.getImages());
	}

	public void terminate() {
		client.shutdown();
		client = null;
	}

}
