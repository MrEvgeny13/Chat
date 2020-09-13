package main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private static Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = ConsoleHelper.readInt();

        try (ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Server started");
            while (true) {
                Handler handler = new Handler(serverSocket.accept());
                handler.start();
            }
        }
        catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    public static void sendBroadcastMessage(Message message) {
        for (Map.Entry<String, Connection> pair : connectionMap.entrySet()) {
            try {
                pair.getValue().send(message);
            } catch (IOException e) {
                System.out.println("Message didn't send. Error: " + e.getMessage());
            }
        }
    }

    private static class Handler extends Thread {
        private Socket socket;

        public Handler (Socket socket) {
            this.socket = socket;
        }

        public void run() {
            ConsoleHelper.writeMessage(String.valueOf(socket.getRemoteSocketAddress()));
            String name = null;
            try (Connection connection = new Connection(socket)) {

                name = serverHandshake(connection);
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, name));
                notifyUsers(connection, name);
                serverMainLoop(connection, name);
            }
            catch (IOException | ClassNotFoundException e) {
                ConsoleHelper.writeMessage("Произошла ошибка при обмене данными с удаленным адресом.");
            }
            if(name != null) {
                connectionMap.remove(name);
                sendBroadcastMessage(new Message(MessageType.USER_REMOVED, name));
            }
            ConsoleHelper.writeMessage("Connection is cosed!");
        }

        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException {
            Message nameRequest = new Message(MessageType.NAME_REQUEST, "Введите, пожалуйста, ваше имя!");
            Message query;
            do {
                connection.send(nameRequest);
                query = connection.receive();
            }
            while (query.getType() != MessageType.USER_NAME
                    || query.getData().isEmpty()
                    || connectionMap.containsKey(query.getData()));

            connectionMap.put(query.getData(), connection);
            connection.send(new Message(MessageType.NAME_ACCEPTED, "Name accepted"));

            return query.getData();
        }

        private void notifyUsers(Connection connection, String userName) throws IOException {
            for (Map.Entry<String, Connection> pair : connectionMap.entrySet()) {
                Message request = new Message(MessageType.USER_ADDED, pair.getKey());
                if (!pair.getKey().equals(userName))
                    connection.send(request);
            }
        }

        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException {
            while (true) {
                Message clientMessage = connection.receive();
                if (clientMessage.getType() == MessageType.TEXT)
                    sendBroadcastMessage(new Message(MessageType.TEXT, userName + ": " + clientMessage.getData()));
                else
                    ConsoleHelper.writeMessage("Error!");
            }
        }
    }
}
