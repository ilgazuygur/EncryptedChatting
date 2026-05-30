CryptoChat using Java Sockets and JavaFX 

For this project, I created a simple encrypted chat application using Java Sockets and JavaFX. I wanted to build something that went beyond basic I/O and showed real networking concepts like client-server communication, multithreading, and data encryption.

I started by setting up a Server and a Client that connect to each other over a local network using sockets on port 6000. Then, I built a JavaFX interface where users can enter their name, type messages, and see the conversation in real time.

After that, I added AES encryption using Java's built-in Cipher library. Every message is encrypted before it is sent and decrypted when it is received, so the raw data traveling through the connection is never plain text.

The hardest part was handling sending and receiving at the same time. I solved this by running each operation on its own thread, so the app never freezes while waiting for a message. I also had to coordinate the UI thread with the networking threads using blocking queues and latches.

Overall, this project helped me understand how real chat applications work under the hood, and how encryption can be layered on top of a network connection. It was rewarding to see two separate programs communicate securely in real time.
