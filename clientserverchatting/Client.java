package clientserverchatting;
/**
 * Varinder Singh
 * 
 */
import java.io.*;
import java.net.Socket;

public class Client implements Runnable {
    // server port
    private int serverPort = 6300;

    // writer to server
    private PrintWriter clientRequestWriter;

    // server IP (use 127.0.0.1 if client & server on same Mac)
    private String serverIPAddress = "127.0.0.1";
    // can use your actual IP address

    private Socket chatClientProgramSocket;

    public static void main(String[] args) {
        Client chatProgramClient = new Client();
        chatProgramClient.run();
    }

    @Override
    public void run() {
        try {
            chatClientProgramSocket = new Socket(serverIPAddress, serverPort);
            System.out.println("Connected to server " + serverIPAddress + ":" + serverPort);

            // read responses from server
            BufferedReader chatProgramClientReader = new BufferedReader(
                    new InputStreamReader(chatClientProgramSocket.getInputStream()));

            clientRequestWriter = new PrintWriter(chatClientProgramSocket.getOutputStream(), true);

            // spawn a thread to read user input & send to server
            Thread chatProgramRequestHandlerThread = new Thread(new ClientRequestsHandler(), "client-input");
            chatProgramRequestHandlerThread.setDaemon(true); // don't keep JVM alive after socket closes
            chatProgramRequestHandlerThread.start();

            // print server messages to console
            String serverResponse;
            while ((serverResponse = chatProgramClientReader.readLine()) != null) {
                System.out.println(serverResponse);
            }

            System.out.println("Disconnected from server.");
        } catch (IOException e) {
            // System.out.println("Client error: " + e.getMessage());
        } finally {
            try { if (chatClientProgramSocket != null) chatClientProgramSocket.close(); } catch (IOException ignore) {}
        }
    }

    // reads from console and forwards to server
    class ClientRequestsHandler implements Runnable {
        BufferedReader clientConsoleReader;

        @Override
        public void run() {
            clientConsoleReader = new BufferedReader(new InputStreamReader(System.in));
            String whatToSendToServer;
            try {
                while ((whatToSendToServer = clientConsoleReader.readLine()) != null) {
                    if (whatToSendToServer.equalsIgnoreCase("quit")) {
                        // tell server and close locally so the app exits cleanly
                        clientRequestWriter.println("quit");
                        try { chatClientProgramSocket.close(); } catch (IOException ignore) {}
                        System.out.println("You left the chat.");
                        return; // end input thread
                    }
                    forwardRequestToServer(whatToSendToServer);
                }
            } catch (IOException e) {
                // ignore for simplicity
            }
        }
    }

    // send one line to server
    void forwardRequestToServer(String chat) {
        clientRequestWriter.println(chat);
    }
}
