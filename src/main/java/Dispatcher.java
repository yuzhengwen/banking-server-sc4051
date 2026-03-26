// Dispatcher.java
import handlers.*;
import handlers.TransferHandler;
import model.*;
import util.Marshaller;

import javax.swing.*;
import java.net.DatagramSocket;

public class Dispatcher {

    // One instance of each handler, created once at startup and reused
    private final OpenHandler     openHandler;
    private final CloseHandler    closeHandler;
    private final DepositHandler  depositHandler;
    private final MonitorHandler  monitorHandler;
    private final QueryHandler    queryHandler;
    private final TransferHandler transferHandler;

    private final DedupFilter dedupFilter;
    private final boolean     atMostOnce;

    public Dispatcher(AccountStore store, CallbackRegistry callbacks,
                      DedupFilter dedupFilter, boolean atMostOnce) {
        this.openHandler     = new OpenHandler(store, callbacks);
        this.closeHandler    = new CloseHandler(store, callbacks);
        this.depositHandler = new DepositHandler(store, callbacks);
        this.monitorHandler = new MonitorHandler(store, callbacks);
        this.queryHandler = new QueryHandler(store, callbacks);
        this.transferHandler = new TransferHandler(store, callbacks);
        this.dedupFilter     = dedupFilter;
        this.atMostOnce      = atMostOnce;
    }

    public byte[] dispatch(Message req, DatagramSocket socket) {

        // --- at-most-once check BEFORE executing ---
        // If we've seen this (client, requestId) before, return the cached reply
        // without touching the AccountStore at all.
        if (atMostOnce) {
            byte[] cached = dedupFilter.getCachedReply(
                    req.senderAddress, req.senderPort, req.requestId);
            if (cached != null) {
                System.out.println("[Dedup] Returning cached reply for reqId=" + req.requestId);
                return cached;
            }
        }

        // --- route to the right handler ---
        byte[] reply = route(req, socket);

        // --- cache the reply AFTER executing (at-most-once only) ---
        if (atMostOnce && reply != null) {
            dedupFilter.cacheReply(req.senderAddress, req.senderPort,
                    req.requestId, reply);
        }

        return reply;
    }

    private byte[] route(Message req, DatagramSocket socket) {
        return switch (req.opcode) {
            case OPEN_ACCOUNT -> openHandler.handle(req, socket);
            case CLOSE_ACCOUNT -> closeHandler.handle(req, socket);
            case DEPOSIT_WITHDRAW -> depositHandler.handle(req, socket);
            case REGISTER_MONITOR -> monitorHandler.handle(req, socket);
            case QUERY_BALANCE -> queryHandler.handle(req, socket);
            case TRANSFER_FUNDS -> transferHandler.handle(req, socket);
            default -> new Message(req.requestId, req.opcode,
                    Message.TYPE_REPLY, StatusCode.ERR_INTERNAL,
                    Marshaller.errorBody("Unknown opcode.")).toBytes();
        };
    }
}