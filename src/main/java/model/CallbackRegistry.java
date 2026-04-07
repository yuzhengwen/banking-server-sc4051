package model;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CallbackRegistry {
    // Record stores the ABSOLUTE time when this client dies
    private record Entry(InetAddress address, int port, long expiryTimeMs) {
    }

    private final AccountStore store;
    private final List<Entry> entries = new ArrayList<>();
    private final DatagramSocket serverSocket;

    public CallbackRegistry(AccountStore store, DatagramSocket serverSocket) {
        this.store = store;
        this.serverSocket = serverSocket;
        // Subscribe the Registry to the Store permanently
        store.updateCallbacks.add(this::callbackMethod);
    }

    public synchronized void register(InetAddress address, int port, int intervalSeconds) {
        long expireAt = System.currentTimeMillis() + (intervalSeconds * 1000L);
        entries.add(new Entry(address, port, expireAt));
    }

    private void callbackMethod(String msg, OpResponse<Account> resp) {
        if (resp != null && resp.isSuccess()) {
            Account acc = resp.data();
            // 1. Build the packet ONCE
            byte[] data = preparePacket(msg, acc.accountNumber, acc.currency, acc.balance);
            // 2. Send to everyone
            dispatch(data);
        }
    }

    private synchronized void dispatch(byte[] data) {
        long now = System.currentTimeMillis();
        Iterator<Entry> it = entries.iterator();

        while (it.hasNext()) {
            Entry e = it.next();

            if (now > e.expiryTimeMs) {
                it.remove(); // Only remove this specific client
                continue;
            }

            try {
                serverSocket.send(new DatagramPacket(data, data.length, e.address, e.port));
            } catch (Exception ex) {
                it.remove(); // If the network path is broken, drop them
            }
        }
    }

    private byte[] preparePacket(String msg, int accountNo, Currency currency, float balance) {
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        // Body = accountNo(4) + currency(1) + balance(4) + msgLen(2) + msgBytes(N)
        int bodyLen = 4 + 1 + 4 + 2 + msgBytes.length;
        ByteBuffer pkt = ByteBuffer.allocate(12 + bodyLen);

        // Header
        pkt.putInt(0);           // requestId
        pkt.put((byte) 0xFF);    // opcode
        pkt.put(Message.TYPE_CALLBACK);
        pkt.put(StatusCode.STATUS_OK.code);
        pkt.put((byte) 0);       // reserved
        pkt.putInt(bodyLen);           // body length

        // Body
        pkt.putInt(accountNo);
        pkt.put(currency.code);
        pkt.putFloat(balance);   // ByteBuffer has .putFloat() built-in!
        pkt.putShort((short) msgBytes.length);  // uint16 length prefix
        pkt.put(msgBytes);                      // UTF-8 bytes

        return pkt.array();
    }
}