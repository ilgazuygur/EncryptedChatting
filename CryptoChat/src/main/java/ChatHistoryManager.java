// ChatHistoryManager.java
// This class is in charge of saving and loading chat messages.
// It is also the place where ENCRYPTION-AT-REST happens:
//
//   * Before a message is saved, we ENCRYPT it (using the existing AESUtil).
//   * After a message is loaded, we DECRYPT it so the UI can show readable text.
//
// So if someone opens the chat_<name>.db file directly, they only see scrambled
// text, not your real messages.

import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;

public class ChatHistoryManager {

    private final DatabaseManager db;
    private final SecretKey key; // the same AES key the chat already uses

    public ChatHistoryManager(DatabaseManager db) throws Exception {
        this.db = db;
        // Reuse the project's existing encryption key. We do NOT change AESUtil.
        this.key = AESUtil.getFixedKey();
    }

    // Saves one message for a contact. "plainBody" is the readable text;
    // we encrypt it here before it touches the database.
    public void saveMessage(String contact, String sender, String plainBody, String type, String time) {
        try {
            String encryptedBody = AESUtil.encrypt(plainBody, key);
            db.saveMessage(new Message(contact, sender, encryptedBody, type, time));
        } catch (Exception e) {
            System.out.println("Could not save message: " + e.getMessage());
        }
    }

    // Loads all messages for a contact and decrypts them back to readable text.
    public List<Message> loadMessages(String contact) {
        List<Message> readable = new ArrayList<>();
        for (Message stored : db.getMessages(contact)) {
            try {
                String plainBody = AESUtil.decrypt(stored.getBody(), key);
                readable.add(new Message(
                        stored.getContact(),
                        stored.getSender(),
                        plainBody,
                        stored.getType(),
                        stored.getTime()));
            } catch (Exception e) {
                // If one message can't be decrypted, just skip it instead of crashing.
                System.out.println("Skipping a message that could not be decrypted.");
            }
        }
        return readable;
    }

    // Clears the history of ONE contact only.
    public void clearHistory(String contact) {
        db.clearMessages(contact);
    }
}
