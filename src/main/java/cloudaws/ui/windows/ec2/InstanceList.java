package cloudaws.ui.windows.ec2;

import cloudaws.Main;
import cloudaws.concurrent.Binding;
import cloudaws.ec2.EC2Util;
import cloudaws.ui.windows.WindowConstruction;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public class InstanceList extends WindowConstruction {

	private static final String DEFAULT_TITLE = "Instances";

	private static final int DEFAULT_WIDTH = 50;
	private static final int DEFAULT_HEIGHT = 10;

	private CompletableFuture<List<Instance>> future;
	private final Binding<List<Instance>> instances;
	private String lastFocusd = "";

	private Panel panel, pending;
	private Button closeButton;

	public InstanceList() { this(""); }

	public InstanceList(String title) {
		super(title);
		this.instances = new Binding<>(Main.EC2::getInstances, this::fail, 1000)
				.withDefault(Collections.emptyList())
				.withNotifier(this, this::updateInstances);
		this.instances.start();
	}

	@Override
	protected void buildComponents() {
		this.setHints(Collections.singletonList(Hint.CENTERED));

		panel = new Panel();
		panel.setLayoutManager(
				new GridLayout(1)
						.setLeftMarginSize(2)
						.setRightMarginSize(2)
		);

		pending = new Panel();
		pending.setPreferredSize(new TerminalSize(DEFAULT_WIDTH, 3));
		pending.setLayoutManager(new GridLayout(1));

		AnimatedLabel pendingLabel = new AnimatedLabel("- Loading -");
		char[] rotation = { '\\', '|', '/' };
		for (char c : rotation) {
			pendingLabel.addFrame(String.format("%c Loading %c", c, c));
		}
		pendingLabel.startAnimation(200);

		pendingLabel.setLayoutData(GridLayout.createLayoutData(
				GridLayout.Alignment.CENTER,
				GridLayout.Alignment.CENTER,
				true,
				true
		)).addTo(pending);
		panel.addComponent(pending);

		closeButton = new Button(LocalizedString.Close.toString(), this::cancel)
				.setLayoutData(GridLayout.createLayoutData(
						GridLayout.Alignment.CENTER,
						GridLayout.Alignment.CENTER,
						true,
						false
				));

		panel.addComponent(closeButton);

		this.setComponent(panel);
	}

	private void cancel() {
		if (future != null) {
			future.cancel(true);
			future = null;
		}
		if (instances != null) {
			instances.stop();
			instances.clear();
		}

		this.close();
	}

	private void updateInstances(List<Instance> instances) {
		if (!getTitle().equals(DEFAULT_TITLE)) this.setTitle(DEFAULT_TITLE);
		panel.removeAllComponents();

		if (instances != null && instances.size() > 0) {
			ActionListBox pool = new ActionListBox(new TerminalSize(DEFAULT_WIDTH, Math.min(instances.size(), DEFAULT_HEIGHT)));
			instances.forEach(instance -> {
				String name = EC2Util.getInstanceName(instance), field;
				if (!name.equals("")) {
					field = String.format("▶ %s (%s)", name, instance.getInstanceId());
				}
				else {
					field = "▶ " + instance.getInstanceId();
				}

				pool.addItem(field, () -> {
					lastFocusd = instance.getInstanceId();
					InstanceModal modal = new InstanceModal(instance);

					this.instances.pause(this);
					this.instances.bind(modal, list -> {
						Optional<Instance> updated = list.stream().filter(i -> i.getInstanceId().equals(modal.instance.getInstanceId())).findFirst();
						if (updated.isPresent()) {
							modal.instance = updated.get();
							modal.updateState(updated.get().getState());
						}
						else {
							modal.updateState(null);
						}
					});
					getTextGUI().addWindowAndWait(modal);
					this.instances.unbind(modal);

					this.updateInstances(this.instances.get());
				});
			});

			panel.addComponent(pool);
			pool.takeFocus();

			if (!lastFocusd.equals("")) {
				int lastIndex = IntStream.range(0, instances.size())
						.filter(i -> Objects.nonNull(instances.get(i)))
						.filter(i -> instances.get(i).getInstanceId().equals(lastFocusd))
						.findFirst()
						.orElse(-1);
				if (lastIndex >= 0) {
					pool.setSelectedIndex(lastIndex);
				}
			}
		}
		else {
			panel.addComponent(new Label("There is no available instance."));
		}

		panel.addComponent(new EmptySpace(TerminalSize.ONE));
		panel.addComponent(closeButton);

		// Run only once.
		this.instances.pause(this);
	}

	private boolean fail(Throwable error) {
		this.setTitle("Loading Failed");
		panel.removeComponent(pending);

		System.err.println(error.getMessage());
		Label msg = new Label(
				" Failed to load EC2 instances from AWS.\nMake sure that your PC is connected to network\nand AWS access key is valid."
		).setPreferredSize(new TerminalSize(DEFAULT_WIDTH, 3));
		panel.addComponent(0, msg);
		panel.addComponent(1, new EmptySpace(TerminalSize.ONE));

		return false;
	}

	public static class InstanceModal extends AbstractWindow {
		private Instance instance;

		private final Panel mainPanel;
		private Border addressBoard;

		private final Label stateLabel;
		private static final Map<Integer, TextColor> COLOR_MAP = new HashMap<>();
		static {
			// 0: Pending
			COLOR_MAP.put(0, TextColor.ANSI.YELLOW);
			// 16: Running
			COLOR_MAP.put(16, TextColor.ANSI.GREEN);
			// 32: Shutting-down
			COLOR_MAP.put(32, TextColor.ANSI.BLUE);
			// 48: Terminated
			COLOR_MAP.put(48, TextColor.ANSI.BLACK);
			// 64: Stopping
			COLOR_MAP.put(64, TextColor.ANSI.RED);
			// 80: Stopped
			COLOR_MAP.put(80, TextColor.ANSI.BLACK);
		}

		public InstanceModal(Instance instance) {
			super("Instance Info.");
			this.instance = instance;
			this.mainPanel = new Panel();

			this.stateLabel = new Label("● " + instance.getState().getName());
			TextColor color = COLOR_MAP.getOrDefault(instance.getState().getCode(), TextColor.ANSI.DEFAULT);
			this.stateLabel.setForegroundColor(color);

			buildComponents();
		}

		private void buildComponents() {
			this.setHints(Collections.singletonList(Hint.CENTERED));
			mainPanel.setLayoutManager(new GridLayout(1));

			Panel inner = new Panel().setLayoutManager(new LinearLayout(Direction.HORIZONTAL));

			Panel infoPanel = buildInfoPanel();
			Panel statePanel = buildStatePanel();

			inner.addComponent(statePanel.withBorder(Borders.doubleLine("Status")));
			inner.addComponent(infoPanel.withBorder(Borders.singleLine("General Info.")));

			mainPanel.addComponent(inner);
			// An instance is running (code: 16)
			if (instance.getState().getCode() == 16) {
				Panel addressPanel = buildAddressPanel();
				addressBoard = addressPanel.withBorder(Borders.singleLine("Address Info."));

				mainPanel.addComponent(addressBoard);
			}

			mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
			mainPanel.addComponent(new Button(LocalizedString.Close.toString(), this::close).setLayoutData(
					GridLayout.createLayoutData(
							GridLayout.Alignment.END,
							GridLayout.Alignment.CENTER,
							true,
							false
					)
			));
			this.setComponent(mainPanel);
		}

		private Panel buildInfoPanel() {
			Panel panel = new Panel();
			panel.setLayoutManager(
					new GridLayout(2)
							.setLeftMarginSize(1)
							.setRightMarginSize(1)
			);

			String name = EC2Util.getInstanceName(instance);
			if (!name.equals("")) printField(panel, "Name", name);
			printField(panel, "Instance ID", instance.getInstanceId());
			printField(panel, "AMI Image", instance.getImageId());
			printField(panel, "Arch", instance.getArchitecture());
			printField(panel, "Type", instance.getInstanceType());
			printField(panel, "Region", instance.getPlacement().getAvailabilityZone());

			return panel;
		}

		private Panel buildAddressPanel() {
			Panel panel = new Panel();
			panel.setLayoutManager(
					new GridLayout(2)
							.setLeftMarginSize(1)
							.setRightMarginSize(1)
			);

			printField(panel,"Public IPv4", instance.getPublicIpAddress(), false);
			printField(panel, "Public DNS", instance.getPublicDnsName(), true);

			return panel;
		}

		private Panel buildStatePanel() {
			Panel panel = new Panel();
			panel.setLayoutManager(
					new GridLayout(2)
							.setLeftMarginSize(1)
							.setRightMarginSize(1)
			);
			panel.addComponent(this.stateLabel);
			panel.addComponent(new EmptySpace(TerminalSize.ONE));

			return panel;
		}

		public void updateState(InstanceState newState) {
			if (newState != null) {
				this.stateLabel.setText("● " + newState.getName());
				TextColor color = COLOR_MAP.getOrDefault(newState.getCode(), TextColor.ANSI.DEFAULT);
				this.stateLabel.setForegroundColor(color);

				if (mainPanel.containsComponent(addressBoard)) {
					if (newState.getCode() != 16) {
						mainPanel.removeComponent(addressBoard);
					}
				}
				else if (newState.getCode() == 16) {
					if (addressBoard == null) {
						addressBoard = buildAddressPanel().withBorder(Borders.singleLine("Address Info."));
					}
					mainPanel.addComponent(1, addressBoard);
				}
			}
			else {
				this.stateLabel.setText("● -");
				this.stateLabel.setForegroundColor(TextColor.ANSI.DEFAULT);
			}
		}

		private void printField(Panel panel, String key, String value) {
			printField(panel, key, value, false);
		}

		private void printField(Panel panel, String key, String value, boolean linebreak) {
			Label keyLabel = new Label("- " + key).addStyle(SGR.BOLD);
			keyLabel.setLayoutData(GridLayout.createLayoutData(
					GridLayout.Alignment.BEGINNING,
					GridLayout.Alignment.BEGINNING,
					true,
					false,
					linebreak ? 2 : 1,
					1
			));
			Label valLabel = new Label(value);
			valLabel.setLayoutData(GridLayout.createLayoutData(
					GridLayout.Alignment.BEGINNING,
					GridLayout.Alignment.BEGINNING,
					true,
					false,
					linebreak ? 2 : 1,
					1
			));

			panel.addComponent(keyLabel);
			panel.addComponent(valLabel);
		}
	}
}
