# Client/Server Chat (Java)

Simple TCP chat. The **server** can type `quit` to end the session for both.
The **client** can type `quit` to exit locally (server stays up).

## How to run
```bash
javac Server.java Client.java
# Terminal 1 (start server)
java Server 5000
# Terminal 2 (start client)
java Client localhost 5000
