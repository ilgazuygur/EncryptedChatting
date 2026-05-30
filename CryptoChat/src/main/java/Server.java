//mvn exec:java -Dexec.mainClass=Server
//mvn javafx:run -Djavafx.mainClass=Client
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import javax.crypto.SecretKey;

public class Server {

    public static void main(String[] args) throws Exception {

        //Starting the ChatUI
        Thread uiThread = new Thread(new Runnable() {
        public void run() {
            ChatUI.main(new String[]{});
        }
    });
        uiThread.start();



        //Taking name from the UI
        String name = ChatUI.waitForUsername();
        
        //Server ServerSocket is a tool that creates the possible connection
        //Creating a server that is using the port number 6000
        ServerSocket serverSocket = new ServerSocket(6000);
        
        //Encrypting the data
        SecretKey key = AESUtil.getFixedKey();

        //Socket is the connection itself between the host and remote
        //Assigning the new connection that accept method returns to object socket 
        Socket socket = serverSocket.accept();

        //InputStreamReader is the tool that converts the bytes to text message
        //Converting the bytes thats sent in the connection to text message
        InputStreamReader inputStreamReader = new InputStreamReader(socket.getInputStream());
    
        //BufferedReader is a tool that reads the message line by line
        //Creating a new object to read message line by line from my input 
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        
        //PrintWritter is a tool that sends out the data 
        PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
        
        //Sending the message to other user, using Thread since both sending and
        //receving should work at the same time.
        
        Thread reading = new Thread(new Runnable() {
            public void run(){
                try {
                    String textMessage = bufferedReader.readLine();
                    while (textMessage != null){
                        String decryptedMessage = AESUtil.decrypt(textMessage, key);
                        ChatUI.appendMessage(decryptedMessage);
                        textMessage = bufferedReader.readLine();
                    }
                }
                catch (Exception e) {
                    ChatUI.appendMessage("Connection Closed");
                }
            }
        });

        Thread sending = new Thread(new Runnable() {
        public void run(){
            try {
                while (true){
                    String textMessage = ChatUI.waitForInput();
                    String formatted = name + ": " + textMessage + " | " + LocalTime.now();
                    String cryptedMessage = AESUtil.encrypt(formatted, key);
                    printWriter.println(cryptedMessage);
                    ChatUI.appendMessage(formatted);
                }
            }
            catch (Exception e) {
                ChatUI.appendMessage("Connection Closed");
            }
        }
    });

        reading.start();
        sending.start();
        uiThread.start();
    }
}