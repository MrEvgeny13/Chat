package main.client;

import main.*;
import java.io.*;
import java.net.Socket;

public class Client {

    protected Connection connection;
    private volatile boolean clientConnected;

    public static void main(String[] args) {
        new Client().run();
    }

    public void run() {
        SocketThread socketThread = getSocketThread();
        socketThread.setDaemon(true);
        socketThread.start();
        synchronized (this) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (clientConnected) System.out.println("Соединение установлено. Для выхода наберите команду 'exit'.");
        else System.out.println("Произошла ошибка во время работы клиента.");
        while (clientConnected) {
            String clientMessage = ConsoleHelper.readString();
            if (clientMessage.equals("exit")) break;
            if (shouldSendTextFromConsole()) sendTextMessage(clientMessage);
        }
    }

    protected String getServerAddress() throws Exception {
        return ConsoleHelper.readString();
    }

    protected int getServerPort() throws Exception {
        return ConsoleHelper.readInt();
    }

    protected String getUserName() throws Exception {
        return ConsoleHelper.readString();
    }

    protected boolean shouldSendTextFromConsole() {
        return true;
    }

    protected void sendTextMessage(String text) {
        try {
            Message message = new Message(MessageType.TEXT, text);
            connection.send(message);
        } catch (IOException ex) {
            ConsoleHelper.writeMessage("Error");
            clientConnected = false;
        }
    }

    protected SocketThread getSocketThread() {
        return new SocketThread();
    }

    public class SocketThread extends Thread {
        protected void processIncomingMessage(String message) throws IOException, ClassNotFoundException {
            ConsoleHelper.writeMessage(message);
        }

        protected void informAboutAddingNewUser(String userName) {
            ConsoleHelper.writeMessage(userName + " joined to chat");
        }

        protected void informAboutDeletingNewUser(String userName) {
            ConsoleHelper.writeMessage(userName + " left chat");
        }

        protected void notifyConnectionStatusChanged(boolean clientConnected) {
            synchronized (Client.this) {
                Client.this.clientConnected = clientConnected;
                Client.this.notify();
            }
        }

        protected void clientHandshake() throws Exception {
            while (true) {
                Message message = connection.receive();
                if (message.getType() == MessageType.NAME_REQUEST) {
                    connection.send(new Message(MessageType.USER_NAME, getUserName()));
                } else {
                    if (message.getType() == MessageType.NAME_ACCEPTED) {
                        notifyConnectionStatusChanged(true);
                        return;
                    } else {
                        throw new IOException("Unexpected MessageType");
                    }
                }
            }
        }

        protected void clientMainLoop() throws IOException, ClassNotFoundException {
            while (true) {
                Message message = Client.this.connection.receive();
                if (message.getType() == MessageType.TEXT) {
                    processIncomingMessage(message.getData());
                } else if (message.getType() == MessageType.USER_ADDED) {
                    informAboutAddingNewUser(message.getData());
                } else if (message.getType() == MessageType.USER_REMOVED) {
                    informAboutDeletingNewUser(message.getData());
                } else {
                    throw new IOException("Unexpected MessageType");
                }
            }
        }

        @Override
        public void run() {
            String addressServer = null;
            try {
                addressServer = getServerAddress();
            } catch (Exception e) {
                e.printStackTrace();
            }
            int port = 0;
            try {
                port = getServerPort();
            } catch (Exception e) {
                e.printStackTrace();
            }

            Socket socket = null;

            try {
                socket = new Socket(addressServer, port);
                Connection connection = new Connection(socket);
                Client.this.connection = connection;

                clientHandshake();
                clientMainLoop();

            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
                notifyConnectionStatusChanged(false);
            }

            catch (IOException e) {
                e.printStackTrace();
                notifyConnectionStatusChanged(false);
            }

            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
