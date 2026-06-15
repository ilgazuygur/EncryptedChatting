// Message.java
// Another small "model" class. It represents ONE chat message.
//
// Important: when a Message lives in the database, its "body" is ENCRYPTED text.
// When a Message is shown on screen, its "body" is the normal readable text.
// The ChatHistoryManager is the class that encrypts/decrypts when it saves/loads.

public class Message {

    // We label every message as either normal text or an image.
    // (Images are not implemented yet, but the label is ready for the future.)
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_IMAGE = "IMAGE";

    private final String contact; // which conversation this message belongs to
    private final String sender;  // who wrote it (a username)
    private final String body;    // the message content (encrypted in DB, plain on screen)
    private final String type;    // TYPE_TEXT or TYPE_IMAGE
    private final String time;    // a simple timestamp string

    public Message(String contact, String sender, String body, String type, String time) {
        this.contact = contact;
        this.sender = sender;
        this.body = body;
        this.type = type;
        this.time = time;
    }

    // Getters so other classes can read the values.
    public String getContact() { return contact; }
    public String getSender()  { return sender; }
    public String getBody()    { return body; }
    public String getType()    { return type; }
    public String getTime()    { return time; }
}
