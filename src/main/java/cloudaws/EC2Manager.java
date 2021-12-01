package cloudaws;

import cloudaws.concurrent.Promise;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Async;
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

public class EC2Manager {

	public static final EC2Manager INSTANCE = new EC2Manager();

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

	public Promise<DescribeInstancesResult> getInstances() {
		DescribeInstancesRequest req = new DescribeInstancesRequest();
		return new Promise<>(client.describeInstancesAsync(req));
	}

	public void terminate() {
		client.shutdown();
		client = null;
	}

}
