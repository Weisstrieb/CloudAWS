package cloudaws.ui;

import cloudaws.ui.windows.MainMenu;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import java.io.IOException;

public class MainScreen {
	private Screen screen;
	private MultiWindowTextGUI gui;

	public MainScreen() {
		try {
			DefaultTerminalFactory factory = new DefaultTerminalFactory();
			screen = factory.createScreen();
			gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLACK));

			screen.startScreen();
		} catch (IOException ex) {
			System.err.println("Failed to create a screen. Exit the program.");
			ex.printStackTrace();

			System.exit(1);
		}
	}

	public void show() {
		gui.addWindowAndWait(new MainMenu("Menus"));
	}

	public void collapse() {
		try {
			screen.stopScreen();
		} catch (IOException ex) {
			System.err.println("Failed to collapse the main screen. Exit the program.");
			ex.printStackTrace();

			System.exit(1);
		}
	}
}