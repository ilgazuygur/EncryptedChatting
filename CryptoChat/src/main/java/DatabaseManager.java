// DatabaseManager.java
// This class talks to the local SQLite database file on your computer.
// It is the ONLY class that runs raw SQL. Everything else (contacts, history)
// goes through this class, which keeps the database code in one tidy place.
//
// It does NOT know anything about encryption. It just stores whatever text it is
// given. The ChatHistoryManager is responsible for encrypting before saving.

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    // The "url" tells Java which database file to use.
    // Example: jdbc:sqlite:chat_alice.db
    private final String url;

    // We make a separate database file per username so that, if you run a server
    // window and a client window on the SAME computer, they don't fight over one file.
    public DatabaseManager(String username) {
        // Keep the file name safe: only letters, numbers, "_" and "-".
        String safe;
        if (username == null || username.trim().isEmpty()) {
            safe = "user";
        } else {
            safe = username.replaceAll("[^a-zA-Z0-9_-]", "_");
        }
        this.url = "jdbc:sqlite:chat_" + safe + ".db";
        createTables(); // make sure the tables exist the first time we run
    }

    // Opens a fresh connection to the database file.
    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        // The UI thread reads messages while the network thread writes them.
        // "busy_timeout" tells SQLite to WAIT up to 3 seconds if the file is
        // momentarily in use, instead of failing with "database is locked".
        // This keeps saving and loading reliable during a live chat.
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA busy_timeout = 3000");
        }
        return connection;
    }

    // Creates our two tables if they do not already exist.
    private void createTables() {
        // A table to remember contact names.
        String contactsSql =
                "CREATE TABLE IF NOT EXISTS contacts (" +
                "  id   INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "  name TEXT UNIQUE NOT NULL" +
                ")";

        // A table to remember messages. "body" holds ENCRYPTED text.
        String messagesSql =
                "CREATE TABLE IF NOT EXISTS messages (" +
                "  id      INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "  contact TEXT NOT NULL, " + // which conversation
                "  sender  TEXT, " +          // who wrote it
                "  body    TEXT, " +          // encrypted message text
                "  type    TEXT, " +          // TEXT or IMAGE
                "  time    TEXT" +            // timestamp
                ")";

        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute(contactsSql);
            s.execute(messagesSql);
        } catch (SQLException e) {
            System.out.println("Database setup error: " + e.getMessage());
        }
    }

    // ---------- Contacts ----------

    // Adds a contact. "INSERT OR IGNORE" means: if the name already exists, do nothing.
    public void addContact(String name) {
        String sql = "INSERT OR IGNORE INTO contacts(name) VALUES(?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("addContact error: " + e.getMessage());
        }
    }

    // Returns all contact names, sorted A-Z.
    public List<String> getContactNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM contacts ORDER BY name";
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.out.println("getContactNames error: " + e.getMessage());
        }
        return names;
    }

    // ---------- Messages ----------

    // Saves one message. The body should already be ENCRYPTED before it gets here.
    public void saveMessage(Message m) {
        String sql = "INSERT INTO messages(contact, sender, body, type, time) VALUES(?,?,?,?,?)";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, m.getContact());
            ps.setString(2, m.getSender());
            ps.setString(3, m.getBody());
            ps.setString(4, m.getType());
            ps.setString(5, m.getTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("saveMessage error: " + e.getMessage());
        }
    }

    // Gets all messages for one contact, oldest first.
    // The body is still ENCRYPTED here; ChatHistoryManager decrypts it.
    public List<Message> getMessages(String contact) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT contact, sender, body, type, time FROM messages WHERE contact = ? ORDER BY id";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, contact);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Message(
                            rs.getString("contact"),
                            rs.getString("sender"),
                            rs.getString("body"),
                            rs.getString("type"),
                            rs.getString("time")));
                }
            }
        } catch (SQLException e) {
            System.out.println("getMessages error: " + e.getMessage());
        }
        return list;
    }

    // Deletes the messages of ONE contact only. Other contacts are untouched.
    public void clearMessages(String contact) {
        String sql = "DELETE FROM messages WHERE contact = ?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, contact);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("clearMessages error: " + e.getMessage());
        }
    }
}
