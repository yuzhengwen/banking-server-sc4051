// handlers/OpenHandler.java
package handlers;

import model.*;
import util.Marshaller;

import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class OpenHandler {

    // Handlers don't own any state themselves.
    // They hold references to the shared store and callback registry.
    private final AccountStore     store;
    private final CallbackRegistry callbacks;

    public OpenHandler(AccountStore store, CallbackRegistry callbacks) {
        this.store     = store;
        this.callbacks = callbacks;
    }

    // handle() is the only public method.
    // It receives the parsed Message and the socket (needed to push callbacks).
    // It always returns a byte[] — the serialised reply to send back.
    public byte[] handle(Message req, DatagramSocket socket) {
        // Wrap req.body in a ByteBuffer so we can read fields from it.
        // The fields must be read in the exact order the C++ client wrote them.
        // For OPEN_ACCOUNT the C++ client sends:
        //   currency(1B) | initialBalance(float32) | name(str) | password(str)
        ByteBuffer body = ByteBuffer.wrap(req.body);

        try {
            byte     currByte = util.Marshaller.readByte(body);
            Currency currency = Currency.from(currByte);
            float    balance  = Marshaller.readFloat(body);
            String   name     = Marshaller.readString(body);
            String   password = Marshaller.readString(body);

            // Delegate the actual work to AccountStore
            int accountNo = store.openAccount(name, password, currency, balance);

            // Notify any monitoring clients
            callbacks.pushUpdate(socket, accountNo, currency, balance);

            // Build the success reply body: just the new account number (4 bytes)
            ByteBuffer replyBody = ByteBuffer.allocate(4);
            util.Marshaller.writeInt(replyBody, accountNo);

            // Wrap in a Message and serialise to bytes
            return new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, Message.STATUS_OK,
                    replyBody.array()).toBytes();

        } catch (Exception e) {
            // Any error becomes a STATUS_ERROR reply with the error string as body.
            // The client can read the string and display it to the user.
            return new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, Message.STATUS_ERROR,
                    Marshaller.errorBody(e.getMessage())).toBytes();
        }
    }
}