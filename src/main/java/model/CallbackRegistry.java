// model/CallbackRegistry.java
package model;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class CallbackRegistry {

    private static class Entry {
        final InetAddress address;
        final int port;
        final long expiryMs;   // absolute time in ms when this registration expires

        Entry(InetAddress address, int port, long intervalMs) {
            this.address  = address;
            this.port     = port;
            this.expiryMs = System.currentTimeMillis() + intervalMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryMs;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    // Register a new monitoring client. intervalSeconds is how long they want to watch.
    public synchronized void register(InetAddress address, int port, int intervalSeconds) {
        entries.add(new Entry(address, port, intervalSeconds * 1000L));
    }

    // Push a notification to all live (non-expired) monitors.
    // Called by handlers after every successful account mutation.
    // Notification body: accountNo(int32) | currency(byte) | newBalance(float32)
    public synchronized void pushUpdate(DatagramSocket socket, int accountNo,
                                        Currency currency, float newBalance) {
        // Build the notification datagram
        ByteBuffer body = ByteBuffer.allocate(4 + 1 + 4);
        body.putInt(accountNo);
        body.put(currency.code);
        body.putInt(Float.floatToIntBits(newBalance));

        ByteBuffer pkt = ByteBuffer.allocate(12 + body.capacity());
        pkt.putInt(0);                   // requestId = 0 for server-pushed packets
        pkt.put((byte) 0xFF);            // opcode 0xFF = callback
        pkt.put(Message.TYPE_CALLBACK);
        pkt.put(Message.STATUS_OK);
        pkt.put((byte) 0);               // reserved
        pkt.putInt(body.capacity());
        pkt.put(body.array());
        byte[] data = pkt.array();

        // Send to each registered client, removing any that have expired
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.isExpired()) { it.remove(); continue; }
            try {
                socket.send(new DatagramPacket(data, data.length, e.address, e.port));
            } catch (Exception ex) {
                System.err.println("[Callback] Send failed: " + ex.getMessage());
            }
        }
    }
}