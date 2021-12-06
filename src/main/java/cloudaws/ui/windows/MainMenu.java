package cloudaws.ui.windows;

import cloudaws.ui.windows.ec2.ChangeRegion;
import cloudaws.ui.windows.ec2.CondorStatus;
import cloudaws.ui.windows.ec2.InstanceList;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;

import java.util.*;

public class MainMenu extends WindowConstruction {
	public MainMenu(String title) {
		super(title);
	}

	@Override
	protected void buildComponents() {
		this.setHints(Collections.singletonList(Hint.CENTERED));

		Panel panel = new Panel();
		panel.setLayoutManager(
				new GridLayout(1)
						.setLeftMarginSize(2)
						.setRightMarginSize(2)
		);
		Map<String, Runnable> menus = this.supplyMenus();
		ActionListBox box = new ActionListBox(new TerminalSize(40, Math.min(menus.size(), 10)));
		menus.forEach(box::addItem);

		box.setLayoutData(
				GridLayout.createLayoutData(
						GridLayout.Alignment.FILL,
						GridLayout.Alignment.CENTER,
						true,
						false
				)
		).addTo(panel);
		panel.addComponent(new EmptySpace(TerminalSize.ONE));

		Button closeButton = new Button("Exit", this::close)
				.setLayoutData(GridLayout.createLayoutData(
						GridLayout.Alignment.CENTER,
						GridLayout.Alignment.CENTER,
						true,
						false
				));

		panel.addComponent(closeButton);

		this.setComponent(panel);
	}

	private Map<String, Runnable> supplyMenus() {
		Map<String, Runnable> menus = new LinkedHashMap<>();

		menus.put("Instance Management", () -> {
			getTextGUI().addWindowAndWait(new InstanceList());
		});

		menus.put("Change Regions", () -> {
			getTextGUI().addWindowAndWait(new ChangeRegion());
		});

		menus.put("HTCondor Status", () -> {
			getTextGUI().addWindowAndWait(new CondorStatus());
		});

		return menus;
	}
}
