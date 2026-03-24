package handlers;

import model.AccountStore;
import model.CallbackRegistry;
import model.Message;

import java.net.DatagramSocket;

public abstract class BaseHandler {

    // Handlers don't own any state themselves.
    // They hold references to the shared store and callback registry.
    protected final AccountStore store;
    protected final CallbackRegistry callbacks;

    public BaseHandler(AccountStore store, CallbackRegistry callbacks) {
        this.store = store;
        this.callbacks = callbacks;
    }

    // handle() is the only public method.
    // It receives the parsed Message and the socket (needed to push callbacks).
    // It always returns a byte[] — the serialised reply to send back.
    public abstract byte[] handle(Message req, DatagramSocket socket);
}
