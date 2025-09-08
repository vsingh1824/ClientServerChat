package clientserverchatting;
/**
 * Varinder Singh
 * 
 */
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server implements Runnable {
    private ServerSocket serverSocket;

    // keep server running
    private volatile boolean isRequestStillComing = true;

    // server listening port
    private int listeningPort = 6300;

    // track join order for IDs (1 ↔ 2)
    private static int clientPosition;

    // thread-safe list of client handlers
    private final List<RequestHandler> clientRequestsHandlerList = new CopyOnWriteArrayList<>();

    // reuse a single executor for handlers
    private final ExecutorService handlerThreadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        new Server().run();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(listeningPort);
            System.out.println("Server listening on port " + listeningPort + "...");

            // start a console thread so the server operator can broadcast or shutdown
            startOperatorConsoleThread();

            while (isRequestStillComing) {
                try {
                    // wait for a client
                    Socket chatProgramClientSocket = serverSocket.accept();
                    System.out.println(
                        "Client connected from " + chatProgramClientSocket.getRemoteSocketAddress() +
                        " | total clients (incl. this): " + (clientRequestsHandlerList.size() + 1)
                    );

                    // create handler for this client
                    RequestHandler clientProgramClientReqHandler = new RequestHandler(chatProgramClientSocket);

                    // add & run
                    clientRequestsHandlerList.add(clientProgramClientReqHandler);
                    handlerThreadPool.execute(clientProgramClientReqHandler);

                    clientPosition += 1;
                } catch (SocketException se) {
                    if (!isRequestStillComing) {
                        // server socket closed during shutdown
                        break;
                    } else {
                        System.out.println("Accept error: " + se.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            isRequestStillComing = false;
            System.out.println("Server exception: " + e.getMessage());
        } finally {
            shutdownServer(); // ensure cleanup if we exit run()
        }
    }

    /**
     * Handles a single client.
     */
    class RequestHandler implements Runnable {
        private BufferedReader readerForClientRequests;
        private PrintWriter chatClientWriter;
        private final Socket chatProgramClientSocket;

        private int ID;

        public RequestHandler(Socket clientSocket) {
            this.chatProgramClientSocket = clientSocket;

            if (clientPosition == 0) {
                ID = 1;
            } else {
                ID = 2;
            }
        }

        private int partnerID() {
            return (ID == 1) ? 2 : 1;
        }

        @Override
        public void run() {
            try {
                readerForClientRequests = new BufferedReader(
                        new InputStreamReader(chatProgramClientSocket.getInputStream()));
                chatClientWriter = new PrintWriter(chatProgramClientSocket.getOutputStream(), true);

                // simple welcome
                chatClientWriter.println("Connected. You are Chat " + ID + ". Type messages (or 'quit' to exit).");

                String chatClientRequestsSent;
                // read lines from this client and forward to partner
                while ((chatClientRequestsSent = readerForClientRequests.readLine()) != null) {
                    if (chatClientRequestsSent.equalsIgnoreCase("quit")) {
                        // notify this client and the partner, then close
                        sendMessageToClient("Server: You have quit chatting. Goodbye!", ID);
                        Server.this.sendMessageToClient("[Server] Partner left", partnerID());
                        break; // exit loop -> cleanup in finally
                    }

                    // forward to partner
                    Server.this.sendMessageToClient(chatClientRequestsSent, partnerID());
                }
            } catch (IOException e) {
                System.out.println("An error occurred while processing a request: " + e.getMessage());
            } finally {
                // cleanup and remove this handler
                try { chatProgramClientSocket.close(); } catch (IOException ignore) {}
                clientRequestsHandlerList.remove(this);
                System.out.println("Client " + ID + " disconnected. Remaining clients: " + clientRequestsHandlerList.size());
            }
        }

        // deliver a message to THIS client if IDs match
        public void sendMessageToClient(String message, int PartnerID) {
            if (this.ID == PartnerID && chatClientWriter != null) {
                chatClientWriter.println(message);
            }
        }
    }

    // forward helper: send to the partner with partnerID
    void sendMessageToClient(String broadCastMessage, int partnerID) {
        System.out.println("Forwarding to partner " + partnerID + ": " + broadCastMessage);
        for (RequestHandler handler : clientRequestsHandlerList) {
            handler.sendMessageToClient(broadCastMessage, partnerID);
        }
    }

    // broadcast to ALL connected clients (used by server operator console)
    void sendToAll(String msg) {
        for (RequestHandler h : clientRequestsHandlerList) {
            if (h.chatClientWriter != null) {
                h.chatClientWriter.println(msg);
            }
        }
    }

    // start a background thread that reads from the server console and broadcasts,
    // but if "quit" is typed, shut down the server cleanly.
    private void startOperatorConsoleThread() {
        Thread t = new Thread(() -> {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    if (line.equalsIgnoreCase("quit")) {
                        System.out.println("Shutdown requested by server operator...");
                        shutdownServer();
                        return;
                    }
                    sendToAll("[SERVER] " + line);
                }
            } catch (IOException e) {
                System.out.println("Server console error: " + e.getMessage());
            }
        }, "server-operator");
        t.setDaemon(true); // won't block JVM exit
        t.start();
    }

    // Clean shutdown: stop accepting, close clients, stop executor
    private void shutdownServer() {
        if (!isRequestStillComing) return; // already shutting down
        isRequestStillComing = false;
        System.out.println("Server shutting down...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // unblocks accept()
            }
        } catch (IOException ignore) {}

        // notify & close all clients
        for (RequestHandler h : clientRequestsHandlerList) {
            try {
                if (h.chatClientWriter != null) {
                    h.chatClientWriter.println("[Server] Shutting down.");
                }
                if (h.chatProgramClientSocket != null && !h.chatProgramClientSocket.isClosed()) {
                    h.chatProgramClientSocket.close();
                }
            } catch (IOException ignore) {}
        }
        clientRequestsHandlerList.clear();

        handlerThreadPool.shutdownNow();
        System.out.println("Server shutdown complete.");
    }
}
