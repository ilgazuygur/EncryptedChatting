// Contact.java
// A very small "model" class. It just represents one person in your contact list.
// Right now a contact is only a name (like "Doruk Ersoy"), but keeping it as its
// own class makes it easy to add more details later (a nickname, a picture, etc.).

public class Contact {

    // The contact's name. "final" means once it is set, it never changes.
    private final String name;

    // Constructor: called when we write "new Contact("Doruk Ersoy")".
    public Contact(String name) {
        this.name = name;
    }

    // A "getter" so other classes can read the name.
    public String getName() {
        return name;
    }

    // When JavaFX shows a Contact in a list, it calls toString().
    // Returning the name means the list shows the name nicely.
    @Override
    public String toString() {
        return name;
    }
}
