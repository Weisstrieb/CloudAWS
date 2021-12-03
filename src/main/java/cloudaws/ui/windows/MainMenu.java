package cloudaws.ui.windows;

import com.googlecode.lanterna.gui2.*;

public class MainMenu extends WindowConstruction {
	@Override
	protected void buildComponents() {
		Panel panel = new Panel();
		panel.setLayoutManager(new GridLayout(2));

		panel.addComponent(new Label("TEST"));

		this.setComponent(panel);
	}
}
