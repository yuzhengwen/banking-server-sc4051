package handlers;

import model.AccountStore;
import model.CallbackRegistry;
import model.Message;

import java.net.DatagramSocket;

public class MonitorHandler extends BaseHandler{
    public MonitorHandler(AccountStore store, CallbackRegistry callbacks) {
        super(store, callbacks);
    }

    @Override
    public byte[] handle(Message req, DatagramSocket socket) {
        return new byte[0];
    }
}
