package cloudaws.ui.windows;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;

import java.util.Collections;

public abstract class PendingWindow extends WindowConstruction {
	protected static final int DEFAULT_WIDTH = 50;

	protected Panel panel, pending;
	protected Button closeButton;

	public PendingWindow() {
		this("");
	}

	public PendingWindow(String title) {
		super(title);
		setWidth(DEFAULT_WIDTH);
	}

	public PendingWindow setWidth(int width) {
		int w = width > 0 ? width : DEFAULT_WIDTH;
		this.pending.setPreferredSize(new TerminalSize(w, 3));
		return this;
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

	protected void cancel() {
		this.close();
	};
}
