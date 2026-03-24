import model.AccountStore;
import model.CallbackRegistry;
import model.DedupFilter;
import model.Message;
import util.LossSimulator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

// Server.java — the main loop, wires everything together
public class Server {
    // arg 1: port number (default 2222)
    // arg 2: "amo" for at-most-once semantics, anything else for at-least-once (default "amo")
    // arg 3: packet loss probability (default 0.0)
    public static void main(String[] args) throws Exception {

        // Parse command-line args
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2222;
        boolean atMostOnce = args.length < 2 || args[1].equalsIgnoreCase("amo");
        double lossProb = args.length > 2 ? Double.parseDouble(args[2]) : 0.0;

        // Create the shared objects — one instance of each for the whole server lifetime
        AccountStore store = new AccountStore();
        CallbackRegistry callbacks = new CallbackRegistry();
        DedupFilter dedup = new DedupFilter();
        Dispatcher dispatcher = new Dispatcher(store, callbacks, dedup, atMostOnce);

        DatagramSocket socket = new DatagramSocket(port);
        LossSimulator lossSim = new LossSimulator(socket, lossProb);
        byte[] buf = new byte[65535]; // max UDP packet size

        System.out.println("Listening on port " + port);

        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            lossSim.receive(packet);   // blocks here

            // Copy out only the actual bytes received (not the full 65535 buffer)
            byte[] data = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

            Message req;
            // if packet corrupted or malformed, skip processing and wait for the next one
            try {
                req = Message.fromBytes(data);
                req.senderAddress = packet.getAddress();
                req.senderPort = packet.getPort();
            } catch (Exception e) {
                System.err.println("Malformed packet, skipping: " + e.getMessage());
                continue;
            }

            // Process and reply
            byte[] replyBytes = dispatcher.dispatch(req, socket);
            if (replyBytes == null) continue;

            DatagramPacket reply = new DatagramPacket(
                    replyBytes, replyBytes.length,
                    packet.getAddress(), packet.getPort());
            lossSim.send(reply);
        }
    }
}