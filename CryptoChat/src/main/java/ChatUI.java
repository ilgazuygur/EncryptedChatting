// ChatUI.java
// =====================================================================
// The desktop window (the "user interface") for CryptoChat.
//
// VERY IMPORTANT for the rest of the project:
// Client.java and Server.java only ever call these 5 STATIC methods:
//
//     ChatUI.main(...)             -> open the window
//     ChatUI.waitForUsername()     -> get the name the user typed
//     ChatUI.waitForInput()        -> get the next message to send
//     ChatUI.appendMessage(...)    -> show a message (sent OR received)
//     ChatUI.setStatus(...)        -> show a small status text
//
// We kept all 5 methods EXACTLY the same, so Client.java and Server.java
// did NOT have to be changed at all. Everything new (contacts, database,
// saved history, the modern look) is built around those 5 methods.
//
// A neat trick we use: every message that is sent or received passes through
// appendMessage(). So that single method is where we also SAVE the message
// into the local database. That is why the network code never had to change.
// =====================================================================

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

// Used for the image / screenshot feature.
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;

public class ChatUI extends Application {

    // ---- Things shared with Client.java / Server.java (must stay static) ----

    // A safe "mailbox" that hands typed messages from the UI to the sending code.
    private static final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    // A small gate that makes Client/Server wait until the user has typed a name.
    private static final CountDownLatch startLatch = new CountDownLatch(1);
    private static String savedUsername;

    // ---- Image sending ----
    // An image is sent as a normal encrypted text line that starts with this tag,
    // followed by the picture encoded as Base64 text:  [[IMG]]<base64-data>
    private static final String IMG_PREFIX = "[[IMG]]";
    // Keep images small so the single-line socket does not freeze (~1.5 MB).
    private static final long MAX_IMAGE_BYTES = 1_500_000;

    // ---- App data (managers that handle the database) ----
    private static DatabaseManager db;
    private static ContactManager contactManager;
    private static ChatHistoryManager historyManager;

    // The contact we are currently chatting with. New messages get saved under it.
    private static volatile String activeContact;

    // ---- UI pieces that the static methods need to reach ----
    private static VBox messageBox;      // the column of chat bubbles
    private static ScrollPane chatScroll; // scroll area around the bubbles
    private static Label statusLabel;     // small "Encrypted" badge / status text

    // ---- UI pieces used only inside this window ----
    private Stage stage;
    private ListView<String> contactsList;
    private Label activeContactLabel;
    private TextField inputField;
    private VBox historyBox;
    private Label historyContactLabel;
    private BorderPane rootPane;   // the whole main window
    private VBox sidebar;          // left contacts panel
    private HBox topBar;           // top title bar
    private boolean darkMode = false;

    // =================================================================
    // 1) STARTUP: show the login screen first
    // =================================================================
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("CryptoChat");
        stage.setScene(buildLoginScene());
        stage.show();
        // Note: we do NOT release the latch yet. We wait until the user
        // actually types a name and clicks "Enter chat".
    }

    // The first screen: just a title and a place to type your name.
    private Scene buildLoginScene() {
        Label title = new Label("CryptoChat");
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");

        Label subtitle = new Label("Secure desktop messaging");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        TextField nameField = new TextField();
        nameField.setPromptText("Enter your name...");
        nameField.setMaxWidth(240);
        nameField.setStyle("-fx-background-radius: 8; -fx-padding: 8;");

        Button enterButton = new Button("Enter chat");
        enterButton.setMaxWidth(240);
        enterButton.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 8 16; -fx-font-weight: bold;");

        // Both pressing Enter and clicking the button do the same thing.
        enterButton.setOnAction(e -> onLogin(nameField.getText()));
        nameField.setOnAction(e -> onLogin(nameField.getText()));

        VBox box = new VBox(14, title, subtitle, nameField, enterButton);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.setStyle("-fx-background-color: #f4f6fb;");

        return new Scene(box, 480, 560);
    }

    // Runs when the user finishes the login screen.
    private void onLogin(String typedName) {
        // Use a default name if the box was left empty.
        String name = (typedName == null || typedName.trim().isEmpty()) ? "User" : typedName.trim();
        savedUsername = name;

        // Build the three managers now that we know the username.
        try {
            db = new DatabaseManager(name);
            contactManager = new ContactManager(db);
            historyManager = new ChatHistoryManager(db);
        } catch (Exception e) {
            System.out.println("Could not start database: " + e.getMessage());
        }

        // ---------------------------------------------------------------
        // IMPORTANT: release the latch RIGHT NOW, before any window work.
        // This is what lets Client.java / Server.java continue:
        //   * Server.java moves on to open ServerSocket(6000) and listen.
        //   * Client.java moves on to connect.
        // We do it before building the main window so that NOTHING in the
        // UI (an exception, slow loading, etc.) can ever block the network
        // code. This is the fix for the "Connection refused" problem.
        // ---------------------------------------------------------------
        startLatch.countDown();
        System.out.println("Login complete for '" + name + "'. Network code can now continue (server will start listening).");

        // Now build and show the main chat window. If anything here fails,
        // we catch it so it can never undo the countDown above.
        try {
            stage.setScene(buildMainScene());
            stage.setTitle("CryptoChat - " + name);

            // Make sure there is always at least one contact to chat under.
            refreshContacts();
            if (contactsList.getItems().isEmpty()) {
                contactManager.addContact("General");
                refreshContacts();
            }
            contactsList.getSelectionModel().selectFirst(); // selecting fires selectContact()
        } catch (Exception e) {
            System.out.println("Could not build the main window: " + e.getMessage());
        }
    }

    // =================================================================
    // 2) THE MAIN WINDOW: contacts on the left, tabs on the right
    // =================================================================
    private Scene buildMainScene() {
        rootPane = new BorderPane();

        // ---- Top bar: app name + an "Encrypted" badge ----
        Label appName = new Label("CryptoChat");
        appName.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");
        statusLabel = new Label("End-to-end encrypted");
        statusLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12px;");
        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        topBar = new HBox(10, appName, topSpacer, statusLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 16, 12, 16));
        rootPane.setTop(topBar);

        // ---- Left: the contacts panel ----
        rootPane.setLeft(buildSidebar());

        // ---- Center: the tabs (Chat / History / Settings) ----
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab chatTab = new Tab("Chat", buildChatTab());
        Tab historyTab = new Tab("History", buildHistoryTab());
        Tab settingsTab = new Tab("Settings", buildSettingsTab());
        tabs.getTabs().addAll(chatTab, historyTab, settingsTab);

        // When you switch to the History tab, refresh it for the current contact.
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == historyTab) {
                refreshHistoryTab(activeContact);
            }
        });

        rootPane.setCenter(tabs);

        applyTheme(false); // start in light mode
        return new Scene(rootPane, 820, 600);
    }

    // The left-hand contacts panel: a list + an "Add Contact" button.
    private VBox buildSidebar() {
        Label heading = new Label("Contacts");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        contactsList = new ListView<>();
        VBox.setVgrow(contactsList, Priority.ALWAYS);
        // When you click a contact, open that contact's chat.
        contactsList.getSelectionModel().selectedItemProperty().addListener((obs, oldName, newName) -> {
            if (newName != null) {
                selectContact(newName);
            }
        });

        Button addButton = new Button("+  Add Contact");
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setStyle(
                "-fx-background-color: #2563eb; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 8;");
        addButton.setOnAction(e -> onAddContact());

        sidebar = new VBox(10, heading, contactsList, addButton);
        sidebar.setPadding(new Insets(14));
        sidebar.setPrefWidth(220);
        return sidebar;
    }

    // The Chat tab: a header (who you are chatting with + Clear button),
    // the scrolling bubbles, and a typing row at the bottom.
    private BorderPane buildChatTab() {
        activeContactLabel = new Label("Chat");
        activeContactLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Button clearButton = new Button("Clear chat history");
        clearButton.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 6 12;");
        clearButton.setOnAction(e -> onClearHistory());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(10, activeContactLabel, headerSpacer, clearButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 14, 10, 14));

        // The column of chat bubbles, inside a scroll area.
        messageBox = new VBox(4);
        messageBox.setPadding(new Insets(10));
        chatScroll = new ScrollPane(messageBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setStyle("-fx-background: white; -fx-background-color: white;");

        // The typing row at the bottom.
        inputField = new TextField();
        inputField.setPromptText("Type a message...");
        inputField.setStyle("-fx-background-radius: 8; -fx-padding: 8;");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendButton = new Button("Send");
        sendButton.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 18;");
        sendButton.setOnAction(e -> handleSend());
        inputField.setOnAction(e -> handleSend()); // pressing Enter also sends

        // Buttons for sending a picture or a screenshot.
        Button attachButton = new Button("Attach Image");
        attachButton.setStyle("-fx-background-radius: 8; -fx-padding: 8 12;");
        attachButton.setOnAction(e -> onAttachImage());

        Button screenshotButton = new Button("Send Screenshot");
        screenshotButton.setStyle("-fx-background-radius: 8; -fx-padding: 8 12;");
        screenshotButton.setOnAction(e -> onSendScreenshot());

        HBox typingRow = new HBox(8, attachButton, screenshotButton, inputField, sendButton);
        typingRow.setPadding(new Insets(10, 14, 12, 14));

        BorderPane chatPane = new BorderPane();
        chatPane.setTop(header);
        chatPane.setCenter(chatScroll);
        chatPane.setBottom(typingRow);
        return chatPane;
    }

    // The History tab: a read-only list of all saved messages for the selected contact.
    private BorderPane buildHistoryTab() {
        historyContactLabel = new Label("Saved history");
        historyContactLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        HBox header = new HBox(historyContactLabel);
        header.setPadding(new Insets(12, 14, 8, 14));

        historyBox = new VBox(6);
        historyBox.setPadding(new Insets(10, 14, 10, 14));
        ScrollPane scroll = new ScrollPane(historyBox);
        scroll.setFitToWidth(true);

        BorderPane pane = new BorderPane();
        pane.setTop(header);
        pane.setCenter(scroll);
        return pane;
    }

    // The Settings tab: read-only info + a dark mode toggle.
    private VBox buildSettingsTab() {
        Label heading = new Label("Settings");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Label nameInfo = new Label("Your name: " + savedUsername);
        Label serverInfo = new Label("Server: 127.0.0.1 : 6000  (fixed inside Client.java / Server.java)");
        Label dbInfo = new Label("Database file: chat_" + safeName(savedUsername) + ".db (saved on this computer)");

        Label encInfo = new Label(
                "Encryption: AES-128.\n" +
                "Messages are encrypted while travelling over the network AND\n" +
                "encrypted again when stored in the local database (encrypted at rest).");
        encInfo.setStyle("-fx-text-fill: #16a34a;");

        ToggleButton darkToggle = new ToggleButton("Dark mode");
        darkToggle.setOnAction(e -> applyTheme(darkToggle.isSelected()));

        VBox box = new VBox(12, heading, nameInfo, serverInfo, dbInfo, encInfo, darkToggle);
        box.setPadding(new Insets(20));
        return box;
    }

    // =================================================================
    // 3) ACTIONS the buttons trigger
    // =================================================================

    // "Add Contact" button: pops up a small box to type a name.
    private void onAddContact() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Contact");
        dialog.setHeaderText(null);
        dialog.setContentText("Contact name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name != null && !name.trim().isEmpty()) {
                contactManager.addContact(name);
                refreshContacts();
                contactsList.getSelectionModel().select(name.trim()); // open it right away
            }
        });
    }

    // Open one contact's conversation: load and show their saved messages.
    private void selectContact(String name) {
        activeContact = name;
        activeContactLabel.setText("Chat with: " + name);
        messageBox.getChildren().clear();

        // Load this contact's old messages from the database (decrypted for display).
        if (historyManager != null) {
            for (Message m : historyManager.loadMessages(name)) {
                boolean mine = m.getSender() != null && m.getSender().equals(savedUsername);
                // An image message is drawn as a picture; everything else as text.
                if (Message.TYPE_IMAGE.equals(m.getType())) {
                    addImageBubble(m.getSender(), m.getBody(), m.getTime(), mine);
                } else {
                    addBubble(m.getSender(), m.getBody(), m.getTime(), mine, false);
                }
            }
        }
        refreshHistoryTab(name);
    }

    // "Clear chat history" button: deletes ONLY the current contact's messages,
    // and asks for confirmation first.
    private void onClearHistory() {
        if (activeContact == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete all saved messages with \"" + activeContact + "\"?\nThis cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Clear chat history");
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                historyManager.clearHistory(activeContact);
                messageBox.getChildren().clear();
                refreshHistoryTab(activeContact);
            }
        });
    }

    // Called when the user sends a message. It just drops the text into the
    // "mailbox"; Client/Server pick it up, encrypt it, and send it. The message
    // then comes back through appendMessage(), which is where it gets shown+saved.
    private void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        // Safety net: make sure a contact is selected so the message has a home.
        if (activeContact == null) {
            contactManager.addContact("General");
            refreshContacts();
            contactsList.getSelectionModel().select("General");
        }
        inputQueue.offer(text);
        inputField.clear();
    }

    // "Attach Image" button: pick a PNG/JPG file and send it.
    private void onAttachImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an image (PNG or JPG)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return; // user cancelled
        }
        // Size limit so the socket does not freeze on a huge picture.
        if (file.length() > MAX_IMAGE_BYTES) {
            showWarning("Image too large",
                    String.format("Please choose an image smaller than %.1f MB.%nYours is %.1f MB.",
                            MAX_IMAGE_BYTES / 1_000_000.0, file.length() / 1_000_000.0));
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            sendImage(Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            showWarning("Could not read image", e.getMessage());
        }
    }

    // "Send Screenshot" button: capture the whole screen and send it.
    private void onSendScreenshot() {
        try {
            // Take the picture of the screen.
            Robot robot = new Robot();
            Rectangle area = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage shot = robot.createScreenCapture(area);

            // Shrink it (max width 1000) so the file stays small.
            int maxWidth = 1000;
            int w = shot.getWidth();
            int h = shot.getHeight();
            double scale = (w > maxWidth) ? (double) maxWidth / w : 1.0;
            int newW = Math.max(1, (int) (w * scale));
            int newH = Math.max(1, (int) (h * scale));
            BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.drawImage(shot, 0, 0, newW, newH, null);
            g.dispose();

            // Turn it into compressed JPG bytes.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", out);
            byte[] bytes = out.toByteArray();

            if (bytes.length > MAX_IMAGE_BYTES) {
                showWarning("Screenshot too large",
                        String.format("The screenshot is %.1f MB after shrinking. Try attaching a smaller image instead.",
                                bytes.length / 1_000_000.0));
                return;
            }
            sendImage(Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            // On macOS, full-screen capture needs Screen Recording permission.
            showWarning("Screenshot not available",
                    "Could not capture the screen:\n" + e.getMessage() +
                    "\n\nOn macOS, allow Screen Recording for your terminal / VS Code in:\n" +
                    "System Settings -> Privacy & Security -> Screen Recording,\nthen restart the app.");
        }
    }

    // Shared helper: put an image (already Base64 text) into the send queue.
    // Client/Server then encrypt and send it exactly like a normal message.
    private void sendImage(String base64) {
        // Safety net: make sure a contact is selected so the image has a home.
        if (activeContact == null) {
            contactManager.addContact("General");
            refreshContacts();
            contactsList.getSelectionModel().select("General");
        }
        inputQueue.offer(IMG_PREFIX + base64);
    }

    // A small warning popup.
    private void showWarning(String header, String body) {
        Alert alert = new Alert(Alert.AlertType.WARNING, body, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    // =================================================================
    // 4) HELPERS that update the screen
    // =================================================================

    // Reload the contact list from the database.
    private void refreshContacts() {
        if (contactsList == null || contactManager == null) {
            return;
        }
        String previouslySelected = contactsList.getSelectionModel().getSelectedItem();
        contactsList.getItems().setAll(contactManager.getContactNames());
        if (previouslySelected != null && contactsList.getItems().contains(previouslySelected)) {
            contactsList.getSelectionModel().select(previouslySelected);
        }
    }

    // Rebuild the read-only History tab for one contact.
    private void refreshHistoryTab(String contact) {
        if (historyBox == null) {
            return;
        }
        historyBox.getChildren().clear();
        if (contact == null) {
            historyContactLabel.setText("Saved history");
            return;
        }
        historyContactLabel.setText("Saved history for: " + contact);
        if (historyManager == null) {
            return;
        }
        List<Message> messages = historyManager.loadMessages(contact);
        if (messages.isEmpty()) {
            historyBox.getChildren().add(new Label("No saved messages yet."));
            return;
        }
        for (Message m : messages) {
            if (Message.TYPE_IMAGE.equals(m.getType())) {
                // Show a small thumbnail for image messages.
                VBox item = new VBox(2);
                item.getChildren().add(new Label("[" + m.getTime() + "]  " + m.getSender() + ":  (image)"));
                try {
                    byte[] bytes = Base64.getDecoder().decode(m.getBody());
                    // Same idea as the chat preview: load full-res, limit only the
                    // display width, so the thumbnail stays sharp on Retina.
                    Image full = new Image(new ByteArrayInputStream(bytes));
                    ImageView thumb = new ImageView(full);
                    thumb.setPreserveRatio(true);
                    thumb.setSmooth(true);
                    thumb.setFitWidth(Math.min(160, full.getWidth()));
                    item.getChildren().add(thumb);
                } catch (Exception ignore) {
                    // If it cannot be decoded, just show the text label above.
                }
                historyBox.getChildren().add(item);
            } else {
                Label line = new Label("[" + m.getTime() + "]  " + m.getSender() + ":  " + m.getBody());
                line.setWrapText(true);
                historyBox.getChildren().add(line);
            }
        }
    }

    // Add one chat bubble to the chat area.
    // mine   = true  -> blue bubble on the right (your message)
    // system = true  -> a small grey note in the middle (e.g. "Connection Closed")
    private static void addBubble(String sender, String body, String time, boolean mine, boolean system) {
        Platform.runLater(() -> {
            if (messageBox == null) {
                return;
            }
            if (system) {
                Label note = new Label(body);
                note.setStyle("-fx-text-fill: #9ca3af; -fx-font-style: italic; -fx-font-size: 11px;");
                HBox row = new HBox(note);
                row.setAlignment(Pos.CENTER);
                row.setPadding(new Insets(4));
                messageBox.getChildren().add(row);
            } else {
                Label who = new Label(mine ? "You" : sender);
                who.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (mine ? "#dbeafe" : "#6b7280") + ";");

                Label text = new Label(body);
                text.setWrapText(true);
                text.setMaxWidth(320);
                text.setStyle("-fx-text-fill: " + (mine ? "white" : "#111827") + ";");

                Label when = new Label(time);
                when.setStyle("-fx-font-size: 9px; -fx-text-fill: " + (mine ? "#dbeafe" : "#9ca3af") + ";");

                VBox bubble = new VBox(2, who, text, when);
                bubble.setPadding(new Insets(8, 12, 8, 12));
                bubble.setMaxWidth(360);
                bubble.setStyle(mine
                        ? "-fx-background-color: #2563eb; -fx-background-radius: 14;"
                        : "-fx-background-color: #e5e7eb; -fx-background-radius: 14;");

                HBox row = new HBox(bubble);
                row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                row.setPadding(new Insets(3, 8, 3, 8));
                messageBox.getChildren().add(row);
            }
            // Always scroll to the newest message.
            Platform.runLater(() -> chatScroll.setVvalue(1.0));
        });
    }

    // Add one IMAGE bubble to the chat area (a small picture preview).
    // base64Data is the picture encoded as Base64 text.
    private static void addImageBubble(String sender, String base64Data, String time, boolean mine) {
        Platform.runLater(() -> {
            if (messageBox == null) {
                return;
            }
            Label who = new Label(mine ? "You" : sender);
            who.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (mine ? "#dbeafe" : "#6b7280") + ";");

            VBox bubble = new VBox(4, who);
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Data);
                // Load the picture at its FULL resolution, then only limit the
                // DISPLAY width. This keeps it sharp on Retina screens. (Loading a
                // pre-shrunk image and then showing it would stretch it back up and
                // look blurry.)
                Image full = new Image(new ByteArrayInputStream(bytes));
                ImageView view = new ImageView(full);
                view.setPreserveRatio(true); // never stretch or squash
                view.setSmooth(true);
                // Show at most 240 wide, but never wider than the real image
                // (so a small image is not blown up and blurred).
                view.setFitWidth(Math.min(240, full.getWidth()));
                view.setStyle("-fx-cursor: hand;");
                // Click the preview to open the full-size picture in its own window.
                view.setOnMouseClicked(e -> showFullImage(full));
                bubble.getChildren().add(view);
            } catch (Exception ex) {
                Label broken = new Label("[image could not be shown]");
                broken.setStyle("-fx-text-fill: " + (mine ? "white" : "#111827") + ";");
                bubble.getChildren().add(broken);
            }

            Label when = new Label(time);
            when.setStyle("-fx-font-size: 9px; -fx-text-fill: " + (mine ? "#dbeafe" : "#9ca3af") + ";");
            bubble.getChildren().add(when);

            bubble.setPadding(new Insets(8, 12, 8, 12));
            bubble.setMaxWidth(280);
            bubble.setStyle(mine
                    ? "-fx-background-color: #2563eb; -fx-background-radius: 14;"
                    : "-fx-background-color: #e5e7eb; -fx-background-radius: 14;");

            HBox row = new HBox(bubble);
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 8, 3, 8));
            messageBox.getChildren().add(row);

            Platform.runLater(() -> chatScroll.setVvalue(1.0));
        });
    }

    // Open a picture at a larger size in its own window (when a preview is clicked).
    private static void showFullImage(Image image) {
        ImageView big = new ImageView(image);
        big.setPreserveRatio(true);
        big.setSmooth(true);
        // Up to 880 wide, but never larger than the real image.
        big.setFitWidth(Math.min(880, image.getWidth()));

        ScrollPane scroll = new ScrollPane(big);
        scroll.setPannable(true);

        Stage popup = new Stage();
        popup.setTitle("Image");
        popup.setScene(new Scene(scroll, 900, 700));
        popup.show();
    }

    // Switch the window colours between light and dark.
    private void applyTheme(boolean dark) {
        this.darkMode = dark;
        String background = dark ? "#1f2430" : "#f4f6fb";
        String panel = dark ? "#2a3140" : "#ffffff";
        String border = dark ? "#374151" : "#e5e7eb";

        rootPane.setStyle("-fx-background-color: " + background + ";");
        topBar.setStyle("-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 0 0 1 0;");
        sidebar.setStyle("-fx-background-color: " + panel + "; -fx-border-color: " + border + "; -fx-border-width: 0 1 0 0;");
    }

    // Same name-cleaning rule the database uses, so Settings shows the real file name.
    private static String safeName(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "user";
        }
        return username.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // =================================================================
    // 5) THE 5 STATIC METHODS Client.java / Server.java rely on.
    //    Their names and shapes are kept EXACTLY as before.
    // =================================================================

    // Show a message that was sent or received, AND save it to the database.
    public static void appendMessage(String message) {
        if (message == null) {
            return;
        }

        // Messages from Client/Server look like:  "Bob: hello there | 14:32:05"
        // We split that into sender / body / time.
        int firstColon = message.indexOf(": ");
        if (firstColon < 0) {
            // No "name:" part -> this is a system note like "Connection Closed".
            // Show it, but do not save it.
            addBubble(null, message, "", false, true);
            return;
        }

        String sender = message.substring(0, firstColon);
        String rest = message.substring(firstColon + 2);
        String body = rest;
        String time = "";
        int lastBar = rest.lastIndexOf(" | ");
        if (lastBar >= 0) {
            body = rest.substring(0, lastBar);
            time = rest.substring(lastBar + 3);
        }

        // Is this an image message? Image lines start with the [[IMG]] tag.
        boolean isImage = body.startsWith(IMG_PREFIX);
        // For images we store only the Base64 part (without the tag).
        String storedBody = isImage ? body.substring(IMG_PREFIX.length()) : body;
        String type = isImage ? Message.TYPE_IMAGE : Message.TYPE_TEXT;

        // Save under whichever contact is currently open (the body is encrypted
        // inside ChatHistoryManager, so images are encrypted at rest too).
        String contact = (activeContact != null) ? activeContact : "General";
        if (historyManager != null) {
            historyManager.saveMessage(contact, sender, storedBody, type, time);
        }

        // "mine" decides the bubble colour and side.
        boolean mine = sender.equals(savedUsername);
        if (isImage) {
            addImageBubble(sender, storedBody, time, mine);
        } else {
            addBubble(sender, storedBody, time, mine, false);
        }
    }

    // Wait until the user types a message, then hand it to the sender.
    public static String waitForInput() throws Exception {
        return inputQueue.take();
    }

    // Wait until the user has typed a name on the login screen.
    public static String waitForUsername() throws InterruptedException {
        startLatch.await();
        return savedUsername;
    }

    // Update the little status text at the top.
    public static void setStatus(String status) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(status);
            }
        });
    }

    // Open the window. (Client.java / Server.java call this on a background thread.)
    public static void main(String[] args) {
        launch(args);
    }
}
