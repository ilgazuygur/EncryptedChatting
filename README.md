# CryptoChat

CryptoChat is a Java desktop encrypted chat application built with JavaFX.
It allows a server and a client to communicate through an encrypted socket connection. The app also includes a modern user interface, local contacts, encrypted chat history, image/screenshot support, and SQLite database storage.

---

## Features

* Encrypted client-server messaging
* JavaFX desktop interface
* Login / username screen
* Contact list system
* Separate chat history for each contact
* Local SQLite database
* Encrypted saved messages
* Clear history for only the selected contact
* Image sending support
* Screenshot sending support
* Chat history loads again after reopening the app

---

## Technologies Used

* Java
* JavaFX
* Maven
* SQLite
* JDBC
* AES encryption
* Socket programming

---

## Project Structure

```text
CryptoChat/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           ├── Client.java
│           ├── Server.java
│           ├── ChatUI.java
│           ├── AESUtil.java
│           ├── Contact.java
│           ├── Message.java
│           ├── DatabaseManager.java
│           ├── ContactManager.java
│           └── ChatHistoryManager.java
```

---

## Main Files

| File                      | Purpose                                               |
| ------------------------- | ----------------------------------------------------- |
| `Server.java`             | Starts the server and waits for the client connection |
| `Client.java`             | Connects to the server and sends/receives messages    |
| `ChatUI.java`             | Main JavaFX user interface                            |
| `AESUtil.java`            | Handles AES encryption and decryption                 |
| `DatabaseManager.java`    | Handles SQLite database connection and tables         |
| `ContactManager.java`     | Manages contacts                                      |
| `ChatHistoryManager.java` | Saves, loads, and clears chat history                 |
| `Contact.java`            | Contact model                                         |
| `Message.java`            | Message model                                         |

---

## How to Run

Open the project folder in terminal:

```bash
cd /path/to/CryptoChat
```

Compile the project:

```bash
mvn clean compile
```

### 1. Start the Server

Open the first terminal and run:

```bash
mvn -Djavafx.mainClass=Server javafx:run
```

When the server window opens, enter a username and continue to the chat screen.

### 2. Start the Client

Open a second terminal and run:

```bash
cd /path/to/CryptoChat
mvn -Djavafx.mainClass=Client javafx:run
```

The server must be started before the client.

---

## How the App Works

1. The server starts first.
2. The client connects to the server using the local IP address and port.
3. Each user enters a username.
4. The user adds or selects a contact.
5. A message is typed in the chat box.
6. The message is encrypted using AES.
7. The encrypted message is sent through the socket connection.
8. The receiver decrypts the message and sees it in the chat.
9. The message is saved in the local SQLite database.
10. When the same contact is opened later, old messages are loaded again.

---

## Local Database

CryptoChat uses SQLite to save contacts and chat messages locally.

Each user has their own local database file. Messages are saved under the selected contact, so every contact has a separate chat history.

Example:

```text
Contact A → saved messages with Contact A
Contact B → saved messages with Contact B
```

This prevents messages from different contacts from mixing.

---

## Encryption

CryptoChat uses AES encryption.

Messages are encrypted before being sent through the socket connection. Saved messages in the SQLite database are also encrypted, so opening the database directly does not show plain readable chat messages.

### Security Note

This project uses a fixed AES key for simplicity. This is suitable for a school project and demonstration, but a real secure messaging app should use stronger key management, such as password-based keys or secure key exchange.

---

## Image and Screenshot Sending

CryptoChat supports sending images and screenshots through the chat.

Supported image formats:

```text
PNG
JPG
JPEG
```

Images are displayed inside the chat as preview bubbles. They are also saved in the local chat history, so they can be loaded again after reopening the app.

Large files may be limited to avoid freezing the socket connection.

---

## Contact System

The app includes a local contact system.

Users can:

* Add a new contact
* Select a contact
* View that contact’s chat history
* Keep histories separate for each contact
* Clear only the selected contact’s chat history

The contact system works as a local organization system for saved conversations.

---

## Clear Chat History

The “Clear Chat History” button deletes only the messages for the currently selected contact.

It does not delete:

* Other contacts
* Other contacts’ messages
* The full database

A confirmation popup appears before deleting messages.

---

## Testing Checklist

After running the app, test these features:

* Server starts correctly
* Client connects correctly
* Text messages send and receive correctly
* Contacts can be added
* Each contact has separate chat history
* Old messages appear after reopening the app
* Clear history deletes only the selected contact’s messages
* Image sending works
* Screenshot sending works
* Image messages appear again after reopening
* Server and client still communicate after switching contacts

---

## Known Limitations

* The app currently supports one server and one client.
* Contacts are local labels for organizing chat history.
* The AES key is hardcoded for simplicity.
* This is not production-level security.
* Very large image files may not be suitable for sending.
* Screenshot quality may depend on the operating system and screen permissions.

---

## Future Improvements

Possible future upgrades:

* Multi-client server support
* Better key exchange system
* Password-based encryption keys
* User authentication
* General file sending support
* Better image preview window
* Online contact status
* Message search
* Dark/light theme improvements
* Export chat history

---

## School Project Explanation

CryptoChat is a desktop encrypted chat application. The main goal of this project is to show how socket programming, encryption, databases, and user interface design can work together in one application.

The project starts with a server and a client. When the client connects, users can send encrypted messages to each other. The app also saves old messages in a local encrypted SQLite database. This means users can close the app, reopen it later, select the same contact, and still see their old chat history.

This project demonstrates:

* Networking with sockets
* Encryption with AES
* JavaFX user interface design
* SQLite database usage
* Local chat history storage
* Contact-based message organization

<img width="787" height="449" alt="Encryption" src="https://github.com/user-attachments/assets/69541ecc-f42f-4eca-a1d7-71912739bd80" />
