package handlers;

import model.*;
import util.Marshaller;

import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class CloseHandler extends BaseHandler {
    public CloseHandler(AccountStore store, CallbackRegistry callbacks) {
        super(store, callbacks);
    }

    @Override
    public byte[] handle(Message req, DatagramSocket socket) {
        // accountNumber (int32) | name (string) | password (string)

        ByteBuffer body = ByteBuffer.wrap(req.body);
        try {
            int accountNo = Marshaller.readInt(body);
            String name = Marshaller.readString(body);
            String password = Marshaller.readString(body);

            OpResponse<Integer> res = store.closeAccount(accountNo, name, password);
            if (res.isSuccess()) {
                return new Message(req.requestId, req.opcode,
                        Message.TYPE_REPLY, StatusCode.STATUS_OK,
                        null).toBytes();
            }
            return new Message(req.requestId, req.opcode, Message.TYPE_REPLY, res.status(), null).toBytes();
        } catch (Exception e) {
            // Any error becomes a STATUS_ERROR reply with the error string as body.
            // The client can read the string and display it to the user.
            return new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, StatusCode.ERR_INTERNAL,
                    Marshaller.errorBody(e.getMessage())).toBytes();
        }
    }
}
