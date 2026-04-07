package util;

import model.*;

import java.net.*;
import java.nio.ByteBuffer;

public class ExperimentClient {

    private static final int TIMEOUT_MS = 4000;
    private static final int MAX_RETRIES = 5;
    private static final int BUFFER_SIZE = 65535;

    private final DatagramSocket socket;
    private final InetAddress serverAddr;
    private final int serverPort;
    private int requestId = 1;

    public ExperimentClient(String host, int port) throws Exception {
        this.serverAddr = InetAddress.getByName(host);
        this.serverPort = port;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(TIMEOUT_MS);
        System.out.println("[Client] Connected to " + host + ":" + port);
    }

    // ------------------------------------------------------------------ low-level send/receive

    private Message sendWithRetry(byte[] packet) throws Exception {
        int id = requestId++;
        ByteBuffer.wrap(packet).putInt(0, id);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.printf("  [send] reqId=%d attempt %d/%d%n", id, attempt, MAX_RETRIES);
            socket.send(new DatagramPacket(packet, packet.length, serverAddr, serverPort));

            try {
                byte[] buf = new byte[BUFFER_SIZE];
                DatagramPacket rp = new DatagramPacket(buf, buf.length);
                socket.receive(rp);
                byte[] data = new byte[rp.getLength()];
                System.arraycopy(rp.getData(), 0, data, 0, rp.getLength());
                Message reply = Message.fromBytes(data);
                System.out.printf("  [recv] reqId=%d status=%s%n",
                        reply.requestId,
                        reply.status == StatusCode.STATUS_OK ? "OK" : "ERROR");
                return reply;
            } catch (SocketTimeoutException e) {
                System.out.printf("  [timeout] %dms — retrying same reqId=%d%n", TIMEOUT_MS, id);
            }
        }
        throw new RuntimeException("No reply after " + MAX_RETRIES + " attempts");
    }

    // ------------------------------------------------------------------ operations

    /**
     * Sends DROP_NEXT control opcode. Server will drop the reply to the next operation.
     */
    private void sendDropNext() throws Exception {
        byte[] packet = new Message(0, OpCode.DROP_NEXT,
                Message.TYPE_REQUEST, StatusCode.STATUS_OK, new byte[0]).toBytes();
        Message reply = sendWithRetry(packet);
        System.out.println("  [drop-next] " + readString(reply));
    }

    private int openAccount(String name, String password, Currency currency, float balance)
            throws Exception {
        ByteBuffer body = ByteBuffer.allocate(256);
        Marshaller.writeByte(body, currency.code);
        Marshaller.writeFloat(body, balance);
        Marshaller.writeString(body, name);
        Marshaller.writeString(body, password);
        body.flip();
        byte[] bodyBytes = new byte[body.limit()];
        body.get(bodyBytes);

        Message reply = sendWithRetry(
                new Message(0, OpCode.OPEN_ACCOUNT,
                        Message.TYPE_REQUEST, StatusCode.STATUS_OK, bodyBytes).toBytes());

        if (reply.status != StatusCode.STATUS_OK)
            throw new RuntimeException("openAccount failed: " + readString(reply));
        int no = ByteBuffer.wrap(reply.body).getInt();
        System.out.printf("  → account %d created for %s (%.2f %s)%n",
                no, name, balance, currency);
        return no;
    }

    private float queryBalance(int accountNo, String name, String password) throws Exception {
        ByteBuffer body = ByteBuffer.allocate(256);
        Marshaller.writeInt(body, accountNo);
        Marshaller.writeString(body, name);
        Marshaller.writeString(body, password);
        body.flip();
        byte[] bodyBytes = new byte[body.limit()];
        body.get(bodyBytes);

        Message reply = sendWithRetry(
                new Message(0, OpCode.QUERY_BALANCE,
                        Message.TYPE_REQUEST, StatusCode.STATUS_OK, bodyBytes).toBytes());

        if (reply.status != StatusCode.STATUS_OK)
            throw new RuntimeException("queryBalance failed: " + readString(reply));
        float bal = ByteBuffer.wrap(reply.body).getFloat();
        System.out.printf("  → balance of account %d = %.2f%n", accountNo, bal);
        return bal;
    }

    private float transfer(int srcNo, int dstNo, String name, String password, float amount)
            throws Exception {
        ByteBuffer body = ByteBuffer.allocate(256);
        Marshaller.writeInt(body, srcNo);
        Marshaller.writeInt(body, dstNo);
        Marshaller.writeFloat(body, amount);
        Marshaller.writeString(body, name);
        Marshaller.writeString(body, password);
        body.flip();
        byte[] bodyBytes = new byte[body.limit()];
        body.get(bodyBytes);

        Message reply = sendWithRetry(
                new Message(0, OpCode.TRANSFER_FUNDS,
                        Message.TYPE_REQUEST, StatusCode.STATUS_OK, bodyBytes).toBytes());

        if (reply.status != StatusCode.STATUS_OK)
            throw new RuntimeException("transfer failed: " + readString(reply));
        float bal = ByteBuffer.wrap(reply.body).getFloat();
        System.out.printf("  → transfer done. New source balance = %.2f%n", bal);
        return bal;
    }

    private String readString(Message msg) {
        try {
            return Marshaller.readString(ByteBuffer.wrap(msg.body));
        } catch (Exception e) {
            return "(unreadable)";
        }
    }

    // ------------------------------------------------------------------ experiment core

    /**
     * Standard experiment:
     * open Alice (1000 SGD) + Bob (0 SGD)
     * optionally arm drop-next immediately before the transfer
     * transfer 200 Alice → Bob
     * verify: correct = Alice 800, Bob 200
     */
    private boolean runExperiment(boolean armDropNext) {
        System.out.println();

        try {
            requestId = 1;

            System.out.println("│  Opening accounts...");
            int aliceNo = openAccount("Alice", "pass1234", Currency.SGD, 1000f);
            int bobNo = openAccount("Bob", "pass5678", Currency.SGD, 0f);

            System.out.println("│  Verifying initial balances...");
            float aliceInit = queryBalance(aliceNo, "Alice", "pass1234");
            float bobInit = queryBalance(bobNo, "Bob", "pass5678");
            System.out.printf("│  Initial: Alice=%.2f Bob=%.2f%n", aliceInit, bobInit);

            if (armDropNext) {
                System.out.println("│  Arming drop-next — transfer reply will be dropped once...");
                sendDropNext();
            }

            System.out.println("│  Transferring 200 from Alice to Bob...");
            transfer(aliceNo, bobNo, "Alice", "pass1234", 200f);

            System.out.println("│  Verifying final balances...");
            float aliceFinal = queryBalance(aliceNo, "Alice", "pass1234");
            float bobFinal = queryBalance(bobNo, "Bob", "pass5678");

            boolean correct = (aliceFinal == 800f && bobFinal == 200f);

            System.out.println("│");
            System.out.printf("│  Expected : Alice=800.00  Bob=200.00%n");
            System.out.printf("│  Actual   : Alice=%-8.2f Bob=%.2f%n", aliceFinal, bobFinal);
            System.out.printf("│  Result   : %s%n",
                    correct ? "✓ CORRECT" : "✗ WRONG — double execution occurred");
            System.out.println("└────────────────────────────────────────────────");
            return correct;

        } catch (Exception e) {
            System.out.println("│  !! EXCEPTION: " + e.getMessage());
            System.out.println("└────────────────────────────────────────────────");
            return false;
        }
    }

    private void runAutoMode() throws Exception {
        System.out.println();
        runExperiment(true);
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 2222;

        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║           Distributed Banking — Experiment Client                ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.println("  Server : " + host + ":" + port);

        ExperimentClient client = new ExperimentClient(host, port);

        client.runAutoMode();
    }
}