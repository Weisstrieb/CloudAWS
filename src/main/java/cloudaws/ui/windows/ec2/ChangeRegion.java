package cloudaws.ui.windows.ec2;

import cloudaws.Main;
import cloudaws.ui.windows.PendingWindow;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.Region;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChangeRegion extends PendingWindow {

	private static final String DEFAULT_TITLE = "Regions";

	private List<Region> regions;
	private ComboBox<String> cRegions;
	private int lastIndex = 0;

	public ChangeRegion() {
		super();
		setWidth(30);

		Main.EC2().availableRegions().thenAccept(res -> {
			this.regions = res;
			this.updatePanel();
		}).exceptionally(err -> {
			this.fail(err);
			return null;
		});
	}

	private void updatePanel() {
		if (!this.getTitle().equals(DEFAULT_TITLE)) this.setTitle(DEFAULT_TITLE);

		panel.removeAllComponents();
		panel.setLayoutManager(new GridLayout(1)
				.setLeftMarginSize(1)
				.setRightMarginSize(1)
		);

		panel.addComponent(new Label("Select a region: "));
		cRegions = new ComboBox<String>(this.regions.stream().map(Region::getRegionName).collect(Collectors.toList()))
				.setReadOnly(true)
				.setPreferredSize(new TerminalSize(30, 1));
		panel.addComponent(cRegions);

		lastIndex = IntStream.range(0, this.regions.size())
				.filter(i -> this.regions.get(i).getRegionName().equals(Main.EC2().getCurrentRegion()))
				.findFirst()
				.orElse(0);
		cRegions.setSelectedIndex(lastIndex);

		panel.addComponent(new EmptySpace(TerminalSize.ONE));
		panel.addComponent(closeButton);

		cRegions.takeFocus();
	}

	private void fail(Throwable error) {
		this.setTitle("Loading Failed");
		panel.removeComponent(pending);

		System.err.println(error.getMessage());
		Label msg = new Label(
				" Failed to load available regions from AWS.\nMake sure that your PC is connected to network\nand AWS access key is valid."
		).setPreferredSize(new TerminalSize(DEFAULT_WIDTH, 3));
		panel.addComponent(0, msg);
		panel.addComponent(1, new EmptySpace(TerminalSize.ONE));

	}

	@Override
	protected void cancel() {
		if (cRegions != null && lastIndex != cRegions.getSelectedIndex()) {
			Main.EC2().changeRegion(Regions.fromName(cRegions.getSelectedItem()));
		}
		this.close();
	}
}
