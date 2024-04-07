package net.nothingtv.game.network.server;

import net.nothingtv.game.network.MessageHandler;
import net.nothingtv.game.network.NetworkReader;
import net.nothingtv.game.network.NetworkWriter;
import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.MessageRegister;
import net.nothingtv.game.network.message.Messages;
import net.nothingtv.game.network.message.impl.AnnounceUser;
import net.nothingtv.game.network.message.impl.GameServerConnect;
import net.nothingtv.game.network.message.impl.GameServerReady;
import net.nothingtv.game.network.message.impl.LoginServerConnect;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * This session handler consists of a reader and a writer for LS - GS communication and is handled by the listening game server
 */
public class CommSession implements Runnable, MessageHandler {

    private static final Logger LOG = Logger.getLogger(CommSession.class.getName());

    private final SocketChannel socket;
    private final GameServer gameServer;
    private NetworkWriter writer;
    private boolean validLSConnection;

    public CommSession(GameServer gameServer, SocketChannel socket) {
        this.gameServer = gameServer;
        this.socket = socket;
    }

    @Override
    public void run() {
        LOG.info("GS: Comm session started");
        new Thread(new NetworkReader(socket, this)).start();
        new Thread(writer = new NetworkWriter(socket)).start();

        validLSConnection = false;

        while (socket.isOpen()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                break;
            }
        }
        LOG.info("GS: Comm session stopped");
    }

    public void handleMessage(Message message) {
        MessageRegister.MessageId id = MessageRegister.MessageId.values[message.getMessageId()];
        switch(id) {
            case LoginServerConnect -> {
                int token;
                LoginServerConnect lsc = (LoginServerConnect) message;
                token = gameServer.checkLSToken(lsc.token);
                if (token == 0) {
                    quickClose();
                    return;
                }
                GameServerConnect reply = Messages.obtain(GameServerConnect.class);
                reply.token = token;
                writer.send(reply);
                validLSConnection = true;
                LOG.info("Game server has a valid connection to login server");
            }
            case AnnounceUser -> {
                if (validLSConnection) {
                    AnnounceUser au = (AnnounceUser) message;
                    gameServer.store(au.login, au.token);
                    GameServerReady reply = Messages.obtain(GameServerReady.class);
                    reply.user = au.login;
                    writer.send(reply);
                }
            }
            default -> LOG.info("GS comm: Unhandled message " + message.getClass().getName());
        }
        Messages.releaseMessage(message);
    }

    private void quickClose() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
