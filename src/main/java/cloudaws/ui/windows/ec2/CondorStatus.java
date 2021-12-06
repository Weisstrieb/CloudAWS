package cloudaws.ui.windows.ec2;

import cloudaws.Main;
import cloudaws.ec2.EC2Utils;
import cloudaws.ssh.EC2SecureShell;
import cloudaws.ui.windows.PendingWindow;
import com.amazonaws.services.ec2.model.Instance;
import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.jcraft.jsch.JSchException;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class CondorStatus extends PendingWindow {

	private static final int DEFAULT_WIDTH = 60;
	private static final String DEFAULT_TITLE = "HTCondor status";
	private static final String COMMAND = "condor_status";

	private List<Instance> instances;
	private String pem = "";

	private Panel inputField;
	private ComboBox<String> collector;

	private Label pathLabel;
	private Button showButton;

	public CondorStatus() {
		super();
		setWidth(30);

		Main.EC2().getInstances().thenAccept(instances -> {
			this.instances = instances.stream().filter(inst -> {
				return inst.getState().getCode() == 16;
			}).collect(Collectors.toList());
			this.instances.sort((i1, i2) -> {
				String n1 = EC2Utils.getInstanceName(i1), n2 = EC2Utils.getInstanceName(i2);
				if (n1.equals("") == n2.equals("")) {
					return n1.compareTo(n2);
				}
				else {
					return n2.length() - n1.length();
				}
			});

			this.updatePanel();
		}).exceptionally(err -> {
			this.fail(err);
			return null;
		});
	}

	private void setKeyPath(File file) {
		if (file == null || !file.isFile()) {
			inputField.setPreferredSize(new TerminalSize(DEFAULT_WIDTH, 2));
			inputField.removeComponent(pathLabel);
		}
		else if (inputField.getChildCount() == 1) {
			inputField.setPreferredSize(new TerminalSize(DEFAULT_WIDTH, 3));
			inputField.addComponent(pathLabel);
		}

		pathLabel.setText(file != null ? file.getName() : "");
		this.pem = file != null ? file.getAbsolutePath() : "";
	}

	private boolean checkPrerequisites() {
		return !collector.getSelectedItem().equals("") && !this.pem.equals("");
	}

	private void setReady() {
		if (!checkPrerequisites()) {
			showButton.setEnabled(false);
			closeButton.takeFocus();
		}
		else {
			showButton.setEnabled(true);
			showButton.takeFocus();
		}
	}

	public void connect() {
		try {
			EC2SecureShell shell = new EC2SecureShell(
					instances.get(collector.getSelectedIndex()).getPublicDnsName(),
					this.pem
			);
			StatusModal modal = new StatusModal(shell.getSSHResponse(COMMAND, 3000));
			getTextGUI().addWindowAndWait(modal);

		} catch (JSchException ex) {
			ex.printStackTrace();
			new MessageDialogBuilder()
					.setTitle("Connection failed")
					.setText(" Failed to establish SSH connection to the collector instance. ")
					.addButton(MessageDialogButton.Close)
					.build()
					.showDialog(getTextGUI());
		}
	}

	public void updatePanel() {
		if (!this.getTitle().equals(DEFAULT_TITLE)) this.setTitle(DEFAULT_TITLE);

		panel.removeAllComponents();

		inputField = new Panel().setLayoutManager(new GridLayout(1)
				.setLeftMarginSize(1)
				.setRightMarginSize(1)
		);

		Panel input = new Panel().setLayoutManager(new GridLayout(2));
		input.setPreferredSize(new TerminalSize(DEFAULT_WIDTH, 2));

		Label cl = new Label("- Collector Instance").addStyle(SGR.BOLD);
		List<String> combo = this.instances
				.stream()
				.map(instance -> {
					String name = EC2Utils.getInstanceName(instance);
					return name.length() > 0 ? name : instance.getInstanceId();
				}).collect(Collectors.toList());
		if (combo.size() == 0) combo.add("");

		collector = new ComboBox<>(combo).setReadOnly(true).setLayoutData(
				GridLayout.createHorizontallyFilledLayoutData(2)
		);

		Label pl = new Label("- RSA Key").addStyle(SGR.BOLD);
		Button select = new Button("Select", () -> {
			FileDialogBuilder builder = new FileDialogBuilder()
					.setTitle("Open file")
					.setDescription("Choose an RSA PEM key file.")
					.setActionLabel("Open");
			builder.setShowHiddenDirectories(true);

			File file = builder.build().showDialog(getTextGUI());
			setKeyPath(file);
			setReady();
		});

		input.addComponent(cl);
		input.addComponent(collector);

		input.addComponent(pl);
		input.addComponent(select);

		inputField.addComponent(input);

		pathLabel = new Label(this.pem).addStyle(SGR.BOLD)
				.setLayoutData(GridLayout.createLayoutData(
						GridLayout.Alignment.END,
						GridLayout.Alignment.CENTER,
						true,
						false
				));
		pathLabel.setForegroundColor(TextColor.ANSI.WHITE);
		pathLabel.setBackgroundColor(TextColor.ANSI.BLACK_BRIGHT);

		panel.addComponent(inputField.withBorder(Borders.singleLine("Prerequisites")));
		panel.addComponent(new EmptySpace(TerminalSize.ONE));

		Panel buttonPanel = new Panel().setLayoutManager(new GridLayout(2)
				.setLeftMarginSize(3)
				.setRightMarginSize(3)
		);
		closeButton = new Button(LocalizedString.Close.toString(), this::cancel);
		showButton = new Button("Show", this::connect).setEnabled(false).setLayoutData(
				GridLayout.createLayoutData(
						GridLayout.Alignment.CENTER,
						GridLayout.Alignment.CENTER,
						true,
						false
				)
		);

		buttonPanel.addComponent(showButton);
		buttonPanel.addComponent(closeButton);

		panel.addComponent(buttonPanel);
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

	public static class StatusModal extends PendingWindow {

		private CompletableFuture<List<String>> future;

		StatusModal(CompletableFuture<List<String>> future) {
			super();
			setWidth(30);

			this.future = future;
			this.future
					.thenAccept(this::onSuccess)
					.exceptionally(err -> {
						this.onFailure(err);
						return null;
					});
		}

		void onSuccess(List<String> list) {
			panel.removeAllComponents();

			Table<String> table = new Table<>(list.get(0).split("\\s+"));
			table.setLayoutData(GridLayout.createLayoutData(
					GridLayout.Alignment.CENTER,
					GridLayout.Alignment.CENTER,
					true,
					true
			));

			int idx = 2;
			while (list.get(idx).length() > 0) {
				table.getTableModel().addRow(list.get(idx++).split("\\s+"));
			}
			panel.addComponent(table);
			panel.addComponent(new EmptySpace(TerminalSize.ONE));

			Table<String> count = new Table<>(list.get(++idx).split("\\s+"));
			count.setLayoutData(GridLayout.createLayoutData(
					GridLayout.Alignment.CENTER,
					GridLayout.Alignment.CENTER,
					true,
					true
			));
			while (++idx < list.size()) {
				if (list.get(idx).length() > 0) {
					List<String> row = Arrays.stream(list.get(idx).split("\\s+")).collect(Collectors.toList());
					row.remove(0);

					count.getTableModel().addRow(row);
				}
			}
			panel.addComponent(count);

			panel.addComponent(new EmptySpace(TerminalSize.ONE));
			panel.addComponent(closeButton);
		}

		void onFailure(Throwable error) {
			error.printStackTrace();
			panel.removeAllComponents();

			this.setTitle("Connection Failed");

			Label msg = new Label(
					" Failed to establish SSH connection to the collector instance.\n Check your network connection and the key file, and there must be at least one instance with collector daemon that is running."
			).setPreferredSize(new TerminalSize(60, 6));

			panel.addComponent(msg);
			panel.addComponent(closeButton);
		}
	}
}
