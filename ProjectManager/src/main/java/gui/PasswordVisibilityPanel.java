package gui;

import auth.AuthenticatedSession;
import auth.VaultService;
import exceptions.DecryptionException;
import exceptions.SessionLockedException;
import models.Password;

import java.awt.BorderLayout;
import java.io.CharArrayReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.Timer;

/** The selected vault entry's password. Call its methods on the Swing event thread. */
public final class PasswordVisibilityPanel extends JPanel
{
    private final JPasswordField field = new JPasswordField(18);
    private final JButton toggle = new JButton("Show");
    private final JLabel status = new JLabel();
    private final char mask = '\u2022';
    private final Timer lockCheck = new Timer(500, event -> checkSession());
    private AuthenticatedSession session;
    private Password entry;
    private boolean visiblePassword;

    public PasswordVisibilityPanel()
    {
        super(new BorderLayout(6, 0));
        JLabel label = new JLabel("Password");
        label.setLabelFor(field);
        field.setEditable(false);
        add(label, BorderLayout.WEST);
        add(field, BorderLayout.CENTER);
        add(toggle, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);
        toggle.addActionListener(event -> togglePassword());
        clearEntry();
    }

    public void setEntry(AuthenticatedSession currentSession, Password selectedEntry)
    {
        clearEntry();
        if (currentSession == null || selectedEntry == null || currentSession.isLocked())
        {
            return;
        }
        session = currentSession;
        entry = selectedEntry;
        hidePassword();
        toggle.setEnabled(true);
        lockCheck.start();
    }

    public void clearEntry()
    {
        lockCheck.stop();
        session = null;
        entry = null;
        hidePassword();
        toggle.setEnabled(false);
    }

    private void hidePassword()
    {
        field.setEchoChar(mask);
        field.setText(entry == null ? "" : "********");
        visiblePassword = false;
        toggle.setText("Show");
        status.setText("");
    }

    private void checkSession()
    {
        if (session != null && session.isLocked())
        {
            clearEntry();
            status.setText("Vault locked. Sign in again.");
        }
    }

    private void togglePassword()
    {
        checkSession();
        if (entry == null)
        {
            return;
        }
        if (visiblePassword)
        {
            hidePassword();
            return;
        }

        char[] password = null;
        try
        {
            password = VaultService.revealPassword(session, entry.getPasswordID());
            field.read(new CharArrayReader(password), null);
            field.setCaretPosition(0);
            field.setEchoChar((char) 0);
            visiblePassword = true;
            toggle.setText("Hide");
            status.setText("");
            checkSession();
        }
        catch (SessionLockedException ex)
        {
            clearEntry();
            status.setText("Vault locked. Sign in again.");
        }
        catch (SQLException | DecryptionException | IOException ex)
        {
            hidePassword();
            status.setText("Could not show this password.");
        }
        finally
        {
            if (password != null)
            {
                Arrays.fill(password, '\0');
            }
        }
    }

    @Override
    public void removeNotify()
    {
        clearEntry();
        super.removeNotify();
    }
}
