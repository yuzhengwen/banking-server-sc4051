package util;// util/util.Marshaller.java

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Marshaller {

    // --- write methods (all take a ByteBuffer positioned at the write point) ---

    public static void writeInt(ByteBuffer buf, int value) {
        buf.putInt(value);   // 4 bytes, big-endian
    }

    public static void writeFloat(ByteBuffer buf, float value) {
        // Convert to the IEEE 754 bit pattern as an int, then write 4 bytes.
        // This is the only safe cross-language way to send a float over UDP.
        buf.putInt(Float.floatToIntBits(value));
    }

    public static void writeByte(ByteBuffer buf, byte value) {
        buf.put(value);      // 1 byte
    }

    public static void writeBool(ByteBuffer buf, boolean value) {
        buf.put(value ? (byte) 1 : (byte) 0);  // 1 byte
    }

    // Strings: write a 2-byte length (uint16) then the UTF-8 bytes.
    // No null terminator — the length prefix is enough.
    public static void writeString(ByteBuffer buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length);  // 2 bytes for length
        buf.put(bytes);                       // N bytes for content
    }

    // Convenience: build a body that contains only a single string.
    // Used by all handlers when returning an error or a simple message.
    public static byte[] errorBody(String message) {
        byte[] strBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(2 + strBytes.length);
        buf.putShort((short) strBytes.length);
        buf.put(strBytes);
        return buf.array();
    }

    // --- read methods (mirror of write, must be called in same order) ---

    public static int readInt(ByteBuffer buf) {
        return buf.getInt();
    }

    public static float readFloat(ByteBuffer buf) {
        // Read 4 bytes as int, then interpret the bit pattern as a float.
        return Float.intBitsToFloat(buf.getInt());
    }

    public static byte readByte(ByteBuffer buf) {
        return buf.get();
    }

    public static boolean readBool(ByteBuffer buf) {
        return buf.get() != 0;
    }

    public static String readString(ByteBuffer buf) {
        // Read the 2-byte length first...
        int len = buf.getShort() & 0xFFFF;  // & 0xFFFF converts signed short to unsigned
        byte[] bytes = new byte[len];
        buf.get(bytes);                      // ...then read exactly that many bytes
        return new String(bytes, StandardCharsets.UTF_8);
    }
}