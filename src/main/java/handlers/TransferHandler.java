package handlers;

import model.*;
import util.Marshaller;

import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class TransferHandler extends BaseHandler {
    public TransferHandler(AccountStore store, CallbackRegistry callbacks) {
        super(store, callbacks);
    }

    @Override
    public byte[] handle(Message req, DatagramSocket socket) {
        // srcAccountNumber (int32) | dstAccountNumber (int32) | amount (float32) | name (string) | password (string)

        ByteBuffer body = ByteBuffer.wrap(req.body);

        try {
            int srcAccountNo = Marshaller.readInt(body);
            int dstAccountNo = Marshaller.readInt(body);

            float amount = Marshaller.readFloat(body);

            String name = Marshaller.readString(body);
            String password = Marshaller.readString(body);

            // Delegate the actual work to AccountStore
            OpResponse<Account> res = store.transfer(srcAccountNo, dstAccountNo, name, password, amount);
            if (res.isSuccess()) {
                // success reply: account balance (float32)
                ByteBuffer replyBody = ByteBuffer.allocate(4);
                util.Marshaller.writeFloat(replyBody, res.data().balance);

                // Wrap in a Message and serialise to bytes
                return new Message(req.requestId, req.opcode,
                        Message.TYPE_REPLY, StatusCode.STATUS_OK,
                        replyBody.array()).toBytes();
            }
            return new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, res.status(),
                    null).toBytes();
        } catch (Exception e) {
            // Any error becomes a STATUS_ERROR reply with the error string as body.
            // The client can read the string and display it to the user.
            return new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, StatusCode.ERR_INTERNAL,
                    Marshaller.errorBody(e.getMessage())).toBytes();
        }
    }
}
