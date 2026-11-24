package gui;

import auth.AuthenticatedSession;
import auth.AccountService;
import exceptions.InvalidCredentialsException;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.Arrays;

public class LoginPanel extends JPanel{

	private static final long serialVersionUID = 1L;

	private final MainWindow window;
	private final JTextField emailField = new JTextField(20);
	private final JPasswordField passwordField = new JPasswordField(20);
	private final JLabel errorLabel = new JLabel(" ");
	private final JButton loginButton = new JButton("Log in");
	
	public LoginPanel(MainWindow window) {
		this.window = window;
		buildLayout();
		
		loginButton.addActionListener(e -> attemptLogin());
		passwordField.addActionListener(e -> attemptLogin());
	}
	
	public void buildLayout() {
		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		
		JLabel title = new JLabel("Password Manager");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
		c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		add(title, c);
		
		c.gridwidth = 1; c.anchor = GridBagConstraints.WEST;
		c.gridy = 1; c.gridx = 0; add(new JLabel("Email:"), c);
		c.gridx = 1;              add(emailField, c);

		c.gridy = 2; c.gridx = 0; add(new JLabel("Password:"), c);
		c.gridx = 1;              add(passwordField, c);

		errorLabel.setForeground(Color.RED);
		c.gridy = 3; c.gridx = 0; c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		add(errorLabel, c);

		c.gridy = 4;
		add(loginButton, c);
	}
	
	private void attemptLogin() {
		
		String email = emailField.getText().trim();
		char[] password = passwordField.getPassword();

		try {
			AuthenticatedSession session = AccountService.login(email, password);
			window.setSession(session);
			passwordField.setText("");
			errorLabel.setText(" ");
			window.showPanel("Dashboard");
			
		} catch (InvalidCredentialsException ex) {
			errorLabel.setText("Invalid email or password.");
			
		} catch (SQLException ex) {
			errorLabel.setText("Database error. Please try again.");
			
		} finally {
			Arrays.fill(password, '\0');
			
		}
	}
}
