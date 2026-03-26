package handlers;

import model.AccountStore;
import model.CallbackRegistry;
import model.Message;
import model.StatusCode;
import util.Marshaller;

import java.net.DatagramSocket;
import java.nio.ByteBuffer;

public class MonitorHandler extends BaseHandler {
    public MonitorHandler(AccountStore store, CallbackRegistry callbacks) {
        super(store, callbacks);
    }

    @Override
    public byte[] handle(Message req, DatagramSocket socket) {
        ByteBuffer body = ByteBuffer.wrap(req.body);
        try {
            int intervalSeconds = Marshaller.readInt(body);
            callbacks.register( req.senderAddress, req.senderPort, intervalSeconds);

            return new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, StatusCode.STATUS_OK,
                    null).toBytes();
        } catch (Exception e) {
            return new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, StatusCode.ERR_INTERNAL,
                    Marshaller.errorBody(e.getMessage())).toBytes();
        }
    }
}
