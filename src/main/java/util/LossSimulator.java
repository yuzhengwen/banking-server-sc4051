package util;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class LossSimulator {

    private final DatagramSocket socket;
    private final double lossProbability;
    private final java.util.Random rng = new java.util.Random();
    public LossSimulator (DatagramSocket socket, double lossProbability) {
        this.socket = socket;
        this.lossProbability = lossProbability;
    }
    public void receive(DatagramPacket packet) throws Exception {
        while (true) {
            socket.receive(packet);
            if (rng.nextDouble() < lossProbability) {
                // Silently discard and wait for the next packet
                System.out.println("[Loss] Dropped incoming packet (simulated)");
                continue;
            }
            return;   // packet survived, return it to the caller
        }
    }

    public void send(DatagramPacket packet) throws Exception {
        if (rng.nextDouble() < lossProbability) {
            System.out.println("[Loss] Dropped outgoing reply (simulated)");
            return;   // silently discard, don't actually send
        }
        socket.send(packet);
    }
}