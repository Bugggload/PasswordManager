package gui;

import javax.swing.*;
import java.awt.BorderLayout;

public class DashboardPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public DashboardPanel(MainWindow window) {
		setLayout(new BorderLayout());
		add(new JLabel("Logged in!", SwingConstants.CENTER), BorderLayout.CENTER);

		JButton logout = new JButton("Log out");
		logout.addActionListener(e -> window.logout());
		add(logout, BorderLayout.SOUTH);
	}
}