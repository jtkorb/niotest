import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by jtk on 2/8/14.
 */
public class CMTestClient implements Runnable {
    private final static int numClients = 100;
    private final static int numPackets = 1000;

    final static String serverLocation = "localhost";
    final static int portLocation = 1111;

    int id;
    int nPackets;

    public static AtomicInteger totalCount = new AtomicInteger(0);

    public static void main(String args[]) {
        Thread[] clients = new Thread[numClients];

        for (int i = 0; i < numClients; i++) {
            clients[i] = new Thread(new CMTestClient(i+1, numPackets));
            clients[i].start();
        }

        for (int i = 0; i < numClients; i++)
            try {
                clients[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        if (numClients * numPackets == totalCount.get())
            System.out.printf("Test successful: received all %d packets\n", totalCount.get());
        else
            System.out.printf("Test FAILED: %d packets expected != %d packets received\n",
                numClients * numPackets, totalCount.get());
    }

    public CMTestClient(int id, int nPackets) {
        this.id = id;
        this.nPackets = nPackets;
    }

    public void run() {
        System.out.printf("client %d starting: generating %d packets\n", id, nPackets);

        try {
            Socket socket = new Socket(serverLocation, portLocation);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());

            Watcher watcher = new Watcher(socket, nPackets);
            watcher.start();

            Thread.sleep(1000);

            for (int i = 0; i < nPackets; i++) {
                outputStreamWriter.write(String.format("message %d %d 5 xxxxx\n", i + 1, nPackets));
                outputStreamWriter.flush();
            }

            System.out.printf("client %d sent %d packets\n", id, nPackets);

            watcher.join();
            socket.close();
        } catch (IOException e) {
            System.err.printf("CLIENT %d FAILED TO START\n", id, nPackets);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Created by jtk on 2/10/14.
     */
    public static class Watcher extends Thread {
        private final Socket socket;
        private final int numPackets;
        BufferedReader bufferedReader;
        private int countPackets = 0;

        public Watcher(Socket socket, int numPackets) {
            this.socket = socket;
            this.numPackets = numPackets;

            try {
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            while (!socket.isOutputShutdown()) {
                try {
                    String line = bufferedReader.readLine();
                    System.out.printf("RECEIVED: %s\n", line);
                    if (line.startsWith("received")) {
                        totalCount.incrementAndGet();
                        String[] fields = line.split(" ");
                        int c = Integer.parseInt(fields[1]);
                        assert c == fields[2].length();
                        if (++countPackets == numPackets) {
                            System.out.printf("RECEIVED all %d packets\n", numPackets);
                            return;
                        }
                    }
                } catch (IOException e) {
                    assert SocketException.class.isInstance(e);  // assert underlying socket is closed
                    return;
                }
            }
        }
    }
}
