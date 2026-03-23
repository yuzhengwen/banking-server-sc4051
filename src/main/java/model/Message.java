// model/Message.java — explained line by line

package model;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class Message {

    // message types (request, reply, callback)
    public static final byte TYPE_REQUEST  = 0;
    public static final byte TYPE_REPLY    = 1;
    public static final byte TYPE_CALLBACK = 2;
    // status codes (only replies)
    public static final byte STATUS_OK     = 0;
    public static final byte STATUS_ERROR  = 1;
    public static final int  HEADER_SIZE   = 12;

    // A message is immutable once created
    public final int    requestId;
    public final OpCode opcode;
    public final byte   msgType;
    public final byte   status;
    public final byte[] body;

    // These two are NOT part of the wire format.
    // The server fills them in after calling recvfrom so handlers
    // know who to reply to.
    public InetAddress senderAddress;
    public int         senderPort;

    public Message(int requestId, OpCode opcode, byte msgType,
                   byte status, byte[] body) {
        this.requestId = requestId;
        this.opcode    = opcode;
        this.msgType   = msgType;
        this.status    = status;
        this.body      = body != null ? body : new byte[0];
    }

    // toBytes() — serialize this Message into a flat byte array.
    // ByteBuffer.allocate(N) gives you a buffer of exactly N bytes,
    // cursor at position 0.
    // Each put* call writes the value and moves the cursor forward.
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + body.length);
        buf.putInt(requestId);   // writes 4 bytes, cursor now at 4
        buf.put(opcode.code);    // writes 1 byte, cursor at 5
        buf.put(msgType);        // writes 1 byte, cursor at 6
        buf.put(status);         // writes 1 byte, cursor at 7
        buf.put((byte) 0);       // reserved byte, cursor at 8
        buf.putInt(body.length); // writes 4 bytes, cursor at 12
        buf.put(body);           // writes body.length bytes
        return buf.array();      // returns the underlying byte[]
    }

    // fromBytes() — the reverse. Wraps the raw byte[] in a ByteBuffer
    // and reads fields in the exact same order as toBytes() wrote them.
    public static Message fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int    requestId = buf.getInt();   // reads bytes 0–3
        byte   opByte   = buf.get();       // reads byte  4
        byte   msgType  = buf.get();       // reads byte  5
        byte   status   = buf.get();       // reads byte  6
        buf.get();                         // reads byte  7 (reserved, discard)
        int    bodyLen  = buf.getInt();    // reads bytes 8–11
        byte[] body     = new byte[bodyLen];
        buf.get(body);                     // reads the next bodyLen bytes
        return new Message(requestId, OpCode.from(opByte), msgType, status, body);
    }
}