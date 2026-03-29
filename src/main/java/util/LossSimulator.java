package util;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class LossSimulator {

    private final DatagramSocket socket;
    private final double lossProbability;
    private final java.util.Random rng = new java.util.Random();

    // When true, the next call to send() will be silently dropped
    // regardless of lossProbability. Resets to false after one drop.
    private boolean dropNextReply = false;

    // Counts how many times send/receive was actually dropped (for logging)
    private int dropCount = 0;

    public LossSimulator(DatagramSocket socket, double lossProbability) {
        this.socket = socket;
        this.lossProbability = lossProbability;
    }

    public void receive(DatagramPacket packet) throws Exception {
        while (true) {
            socket.receive(packet);
            // Random drop on incoming packets
            if (lossProbability > 0.0 && rng.nextDouble() < lossProbability) {
                dropCount++;
                System.out.printf("[LossSimulator] DROPPED incoming request from %s:%d (random %.0f%% loss)%n",
                        packet.getAddress().getHostAddress(), packet.getPort(),
                        lossProbability * 100);
                continue;  // loop back and wait for next packet
            }
            return;   // packet survived, return it to the caller
        }
    }

    public void send(DatagramPacket packet) throws Exception {
        if (dropNextReply) {
            dropNextReply = false;
            dropCount++;
            System.out.printf("[LossSimulator] DROPPED outgoing reply to %s:%d (deterministic)%n",
                    packet.getAddress().getHostAddress(), packet.getPort());
            return;  // silently discard — client will timeout and retry
        }

        // Random drop
        if (lossProbability > 0.0 && rng.nextDouble() < lossProbability) {
            dropCount++;
            System.out.printf("[LossSimulator] DROPPED outgoing reply to %s:%d (random %.0f%% loss)%n",
                    packet.getAddress().getHostAddress(), packet.getPort(),
                    lossProbability * 100);
            return;
        }
        socket.send(packet);
    }

    public void dropNextReply() {
        this.dropNextReply = true;
        System.out.println("[LossSimulator] EXPERIMENT: next reply will be deliberately dropped");
    }

    public int getDropCount() {
        return dropCount;
    }
}