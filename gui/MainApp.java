package gui;

import javax.swing.*;

//Launches the app

public class MainApp {

	public static void main(String[] args) {
		
		SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));

	}
	
}
