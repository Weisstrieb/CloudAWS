package cloudaws.ui.windows;

import com.googlecode.lanterna.gui2.AbstractWindow;

public abstract class WindowConstruction extends AbstractWindow {
	public WindowConstruction() {
		this("");
	}

	public WindowConstruction(String title) {
		super(title);
		buildComponents();
	}

	protected abstract void buildComponents();
}
