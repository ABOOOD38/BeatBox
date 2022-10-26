import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MusicServer {
    private final List<ObjectOutputStream> clientOutputStreams = new ArrayList<>();

    public static void main(String[] args) {
        new MusicServer().go();
    }

    private void go() {
        try {
            ServerSocket serverSocket = new ServerSocket(3500);
            ExecutorService threadPool = Executors.newCachedThreadPool();

            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                clientOutputStreams.add(out);

                threadPool.execute(new ClientHandler(clientSocket));
                System.out.println("Got a connection");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private class ClientHandler implements Runnable {
        private ObjectInputStream in;
        public ClientHandler(Socket socket) {
            try {
                in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Object userNameAndMessage;
            Object beatSequence;
            try {
                while ((userNameAndMessage = in.readObject()) != null) {
                    beatSequence = in.readObject();

                    System.out.println("read two objects");
                    tellEveryone(userNameAndMessage, beatSequence);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void tellEveryone(Object userNameAndMessage, Object beatSequence) {
        try {
            for (ObjectOutputStream clientOutputStream : clientOutputStreams) {
                clientOutputStream.writeObject(userNameAndMessage);
                clientOutputStream.writeObject(beatSequence);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
