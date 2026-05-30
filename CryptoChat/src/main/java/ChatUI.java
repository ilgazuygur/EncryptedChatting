import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

public class ChatUI extends Application {

    private static final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private static final CountDownLatch startLatch = new CountDownLatch(1);
    private static String savedUsername;
    private static TextArea chatArea;
    private static Label statusLabel;
    private TextField inputField;
    private Button sendButton;

    @Override
    public void start(Stage stage) {

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Username");
        dialog.setContentText("Enter your name:");
        savedUsername = dialog.showAndWait().orElse("User");

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);

        inputField = new TextField();
        inputField.setPromptText("Type a message...");

        sendButton = new Button("Send");

        statusLabel = new Label("Connecting...");
        statusLabel.setPadding(new Insets(6));

        HBox bottom = new HBox(8, inputField, sendButton);
        bottom.setPadding(new Insets(8));
        HBox.setHgrow(inputField, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(statusLabel);
        root.setCenter(chatArea);
        root.setBottom(bottom);

        sendButton.setOnAction(e -> handleSend());
        inputField.setOnAction(e -> handleSend());

        stage.setTitle("CryptoChat");
        stage.setScene(new Scene(root, 480, 560));
        stage.show();
        startLatch.countDown();
    }

    private void handleSend() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            inputQueue.offer(text);
            inputField.clear();
        }
    }

    public static void appendMessage(String message) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                chatArea.appendText(message + "\n");
            }
        });
    }

    public static String waitForInput() throws Exception {
        return inputQueue.take();
    }

    public static String waitForUsername() throws InterruptedException {
        startLatch.await();
        return savedUsername;
    }

    public static void setStatus(String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    public static void main(String[] args) {
        launch(args);
    }
}