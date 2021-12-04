package cloudaws.ec2;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;

import java.util.Optional;

public class EC2Util {
	public static String getInstanceName(Instance instance) {
		Optional<Tag> nameTag = instance.getTags().stream().filter(tag -> tag.getKey().equals("Name")).findFirst();
		return nameTag.isPresent() ? nameTag.get().getValue() : "";
	}
}
