package cloudaws.ui.windows.ec2;

import cloudaws.Main;
import cloudaws.ec2.EC2Util;
import cloudaws.ui.windows.WindowConstruction;
import com.amazonaws.services.ec2.model.Instance;
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
	private static final int RELOAD_DELAY = 1000;

	private final Timer asyncTimer = new Timer();

	private CompletableFuture<List<Instance>> future;
	private List<Instance> instances;
	private String lastFocusd = "";

	private Panel panel, pending;
	private Button closeButton;

	public InstanceList() { this(""); }

	public InstanceList(String title) {
		super(title);
		registerAsyncHook();
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
		if (instances != null) instances.clear();
		if (asyncTimer != null) asyncTimer.cancel();

		this.close();
	}

	public void registerAsyncHook() {
		asyncTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				Main.EC2.getInstances().thenAccept(result -> {
					synchronized (this) {
						instances = result;
					}
				});
			}
		}, 0, RELOAD_DELAY);
	}

	public void loadAsync() {
		future = Main.EC2.getInstances();
		future.thenAccept(result -> {
			synchronized (this) {
				instances = result;
				this.updateInstance();
			}
		}).exceptionally(exception -> {
			synchronized (this) {
				instances = Collections.emptyList();
				this.fail(exception);
			}
			return null;
		});
	}

	private void updateInstance() {
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
					System.out.println(instance);
					lastFocusd = instance.getInstanceId();
					InstanceModal modal = new InstanceModal(instance);
					getTextGUI().addWindowAndWait(modal);

					this.updateInstance();
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
	}

	private void fail(Throwable error) {
		this.setTitle("Loading Failed");
		panel.removeComponent(pending);

		System.err.println(error.getMessage());
		Label msg = new Label(
				" Failed to load EC2 instances from AWS.\nMake sure that your PC is connected to network\nand AWS access key is valid."
		).setPreferredSize(new TerminalSize(DEFAULT_WIDTH, 3));
		panel.addComponent(0, msg);
		panel.addComponent(1, new EmptySpace(TerminalSize.ONE));
	}

	public static class InstanceModal extends AbstractWindow {
		private final Instance instance;
		private final Panel mainPanel;

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

			buildComponents();
		}

		protected void buildComponents() {
			this.setHints(Collections.singletonList(Hint.CENTERED));
			mainPanel.setLayoutManager(new GridLayout(1));

			Panel infoPanel = new Panel();
			infoPanel.setLayoutManager(
					new GridLayout(2)
							.setLeftMarginSize(1)
							.setRightMarginSize(1)
			);

			String name = EC2Util.getInstanceName(instance);
			if (!name.equals("")) printField(infoPanel, "Name", name);
			printField(infoPanel, "Instance ID", instance.getInstanceId());
			printField(infoPanel, "AMI Image", instance.getImageId());
			printField(infoPanel, "Arch", instance.getArchitecture());

			Label state = new Label("● " + instance.getState().getName());
			TextColor color = COLOR_MAP.getOrDefault(instance.getState().getCode(), TextColor.ANSI.DEFAULT);
			state.setForegroundColor(color);

			infoPanel.addComponent(state);
			infoPanel.addComponent(new EmptySpace(TerminalSize.ONE));

			mainPanel.addComponent(infoPanel);
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

		private void printField(Panel panel, String key, String value) {
			Label keyLabel = new Label("- " + key).addStyle(SGR.BOLD);
			keyLabel.setLayoutData(GridLayout.createLayoutData(
					GridLayout.Alignment.BEGINNING,
					GridLayout.Alignment.BEGINNING,
					true,
					false
			));
			Label valLabel = new Label(value);
			valLabel.setLayoutData(GridLayout.createLayoutData(
					GridLayout.Alignment.BEGINNING,
					GridLayout.Alignment.BEGINNING,
					true,
					false
			));

			panel.addComponent(keyLabel);
			panel.addComponent(valLabel);
		}
	}
}
