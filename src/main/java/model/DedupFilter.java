// model/DedupFilter.java
package model;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DedupFilter {

    // The key is the triple (address, port, requestId).
    // We need a custom class because Java maps use equals() for key matching.
    private static class Key {
        final InetAddress address;
        final int port;
        final int requestId;

        Key(InetAddress address, int port, int requestId) {
            this.address   = address;
            this.port      = port;
            this.requestId = requestId;
        }

        // equals() and hashCode() are mandatory for HashMap keys.
        // Without these, two Key objects with the same values would
        // not be considered equal, and the dedup lookup would never hit.
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return port == k.port
                    && requestId == k.requestId
                    && Objects.equals(address, k.address);
        }

        @Override public int hashCode() {
            return Objects.hash(address, port, requestId);
        }
    }

    // Maps a (client, requestId) → the reply bytes we already sent
    private final Map<Key, byte[]> history = new HashMap<>();

    // Returns the cached reply if this is a duplicate, or null if it's new.
    public synchronized byte[] getCachedReply(InetAddress addr, int port, int reqId) {
        return history.get(new Key(addr, port, reqId));
    }

    // Store the reply so future duplicates can be short-circuited.
    public synchronized void cacheReply(InetAddress addr, int port, int reqId, byte[] reply) {
        history.put(new Key(addr, port, reqId), reply);
    }
}