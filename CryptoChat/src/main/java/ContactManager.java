// ContactManager.java
// A simple helper that sits on top of the DatabaseManager and deals with contacts.
// The UI talks to this class instead of writing SQL itself.

import java.util.ArrayList;
import java.util.List;

public class ContactManager {

    private final DatabaseManager db;

    public ContactManager(DatabaseManager db) {
        this.db = db;
    }

    // Adds a contact (ignores empty names and trims spaces).
    public void addContact(String name) {
        if (name != null && !name.trim().isEmpty()) {
            db.addContact(name.trim());
        }
    }

    // Returns the contacts as Contact objects (handy if we add fields later).
    public List<Contact> getContacts() {
        List<Contact> list = new ArrayList<>();
        for (String name : db.getContactNames()) {
            list.add(new Contact(name));
        }
        return list;
    }

    // Returns just the names (handy for putting straight into a JavaFX list).
    public List<String> getContactNames() {
        return db.getContactNames();
    }
}
