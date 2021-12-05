package cloudaws.ui.windows.ec2;

import cloudaws.Main;
import cloudaws.concurrent.Promise;
import cloudaws.ui.windows.PendingWindow;
import com.amazonaws.services.ec2.model.*;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InstanceCreation extends PendingWindow {

	private static final String DEFAULT_TITLE = "Create an instance";

	private final RunInstancesRequest request = new RunInstancesRequest();
	private boolean requested = false;

	private List<Image> aImages;
	private List<AvailabilityZone> aZones;
	private List<String> aKeys;
	private List<SecurityGroup> aSecurityGroups;

	private TextBox name;
	private ComboBox<String> cImages, cZones, cKeys, cGroups;

	public InstanceCreation() {
		super();
		// Default setting for free-tier users
		request.withInstanceType(InstanceType.T2Micro)
				.withMinCount(1)
				.withMaxCount(1);
		prepareOptions();
	}

	private void prepareOptions() {
		CompletableFuture<Void> images = Main.EC2().getImages().thenAccept(res -> this.aImages = res);
		CompletableFuture<Void> zones = Main.EC2().avaliableZones().thenAccept(res -> this.aZones = res);
		CompletableFuture<Void> keys = Main.EC2().getKeyPairs().thenAccept(res ->
				this.aKeys = res.stream().map(KeyPairInfo::getKeyName).collect(Collectors.toList())
		);
		CompletableFuture<Void> groups = Main.EC2().getSecurityGroups().thenAccept(res ->
				this.aSecurityGroups = res
		);

		new Promise<>(CompletableFuture.allOf(images, zones, keys, groups)).thenRun(this::updatePanel);
	}

	@Override
	public void cancel() {
		this.close();
	}

	public boolean isRequested() {
		return this.requested;
	}

	private void updatePanel() {
		if (!this.getTitle().equals(DEFAULT_TITLE)) this.setTitle(DEFAULT_TITLE);

		panel.removeAllComponents();
		panel.setLayoutManager(new GridLayout(2)
				.setLeftMarginSize(1)
				.setRightMarginSize(1)
		);

		br();
		name = new TextBox().setValidationPattern(Pattern.compile("[\\d\\w\\- .,]*"));
		name.setPreferredSize(new TerminalSize(30, 1));
		name.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER));
		addField("Name", name);

		LayoutData right = GridLayout.createLayoutData(
				GridLayout.Alignment.END,
				GridLayout.Alignment.CENTER,
				true,
				false
		);

		cImages = new ComboBox<>(this.aImages.stream().map(Image::getName).collect(Collectors.toList()))
				.setReadOnly(true)
				.setLayoutData(right);
		cZones = new ComboBox<>(this.aZones.stream().map(AvailabilityZone::getZoneName).collect(Collectors.toList()))
				.setReadOnly(true)
				.setLayoutData(right);
		cKeys = new ComboBox<>(this.aKeys)
				.setReadOnly(true)
				.setLayoutData(right);
		cGroups = new ComboBox<>(this.aSecurityGroups.stream().map(SecurityGroup::getGroupName).collect(Collectors.toList()))
				.setReadOnly(true)
				.setLayoutData(right);

		addField("Image", cImages);
		addField("Zone", cZones);
		addField("Access Key", cKeys);
		addField("Security Group", cGroups);
		br();

		Button createButton = new Button("Create", () -> {
			if (name.getText().trim().length() == 0) {
				new MessageDialogBuilder()
						.setTitle("Invalid name")
						.setText("  The name of instance must be provided.  ")
						.addButton(MessageDialogButton.Close)
						.build()
						.showDialog(getTextGUI());
				return;
			}

			MessageDialogButton confirm = new MessageDialogBuilder()
					.setTitle("Confirm creation")
					.setText(String.format("  Are you sure to make an instance '%s'?  ", name.getText().trim()))
					.addButton(MessageDialogButton.Yes)
					.addButton(MessageDialogButton.No)
					.build()
					.showDialog(getTextGUI());

			if (confirm.equals(MessageDialogButton.Yes)) {
				request.withTagSpecifications(
								new TagSpecification()
										.withResourceType(ResourceType.Instance.toString())
										.withTags(new Tag("Name", name.getText().trim()))
						)
						.withImageId(aImages.get(cImages.getSelectedIndex()).getImageId())
						.withPlacement(new Placement(cZones.getSelectedItem()))
						.withKeyName(cKeys.getSelectedItem())
						.withSecurityGroupIds(aSecurityGroups.get(cGroups.getSelectedIndex()).getGroupId());

				Main.EC2().createInstance(request);
				requested = true;
				this.close();
			}
		}).setLayoutData(
				GridLayout.createLayoutData(
						GridLayout.Alignment.CENTER,
						GridLayout.Alignment.CENTER,
						true,
						false
				)
		);
		closeButton = new Button(LocalizedString.Cancel.toString(), this::cancel).setLayoutData(
				GridLayout.createLayoutData(
						GridLayout.Alignment.CENTER,
						GridLayout.Alignment.CENTER,
						true,
						false
				)
		);

		panel.addComponent(createButton);
		panel.addComponent(closeButton);

		name.takeFocus();
		this.invalidate();
	}

	private void addField(String name, Component component) {
		panel.addComponent(new Label("- " + name).addStyle(SGR.BOLD));
		panel.addComponent(component);
	}

	private void br() { br(panel); }

	private void br(Panel panel) {
		EmptySpace br = new EmptySpace(TerminalSize.ONE);
		br.setLayoutData(GridLayout.createLayoutData(
				GridLayout.Alignment.BEGINNING,
				GridLayout.Alignment.BEGINNING,
				false,
				false,
				2,
				1
		));
		panel.addComponent(br);
	}
}
