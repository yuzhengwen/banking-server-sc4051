package handlers;

import model.AccountStore;
import model.CallbackRegistry;
import model.Message;
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
            String status = store.transfer(srcAccountNo, dstAccountNo, name, password, amount);

            // Notify any monitoring clients
            //callbacks.pushUpdate(socket, srcAccountNo, currency, balance);

            // success reply: account balance (float32)
            ByteBuffer replyBody = ByteBuffer.allocate(4);
            if (status == null)
                util.Marshaller.writeInt(replyBody, 0);
            else
                throw new Exception(status);

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
