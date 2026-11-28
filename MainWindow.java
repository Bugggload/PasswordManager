package gui;

import auth.AuthenticatedSession;

import javax.swing.*;
import java.awt.CardLayout;

//Handles switching panels

public class MainWindow extends JFrame {

	private static final long serialVersionUID = 1L;
	
	private final CardLayout cards = new CardLayout();
	private final JPanel container = new JPanel(cards);
	private AuthenticatedSession session;

	public MainWindow() {
		setTitle("Password Manager");
		setSize(720, 480);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		
		container.add(new LoginPanel(this), "Login");
		container.add(new DashboardPanel(this), "Dashboard");
		add(container);
		showPanel("Login");
	}
	
	public void showPanel(String name) {
		cards.show(container, name);
	}
	
	public void setSession(AuthenticatedSession session) {
		this.session = session;
	}
	
	public AuthenticatedSession getSession() {
		return this.session;
	}
	
	public void logout() {
		if(session != null) session.lock();
		session = null;
		showPanel("Login");
	}
}
