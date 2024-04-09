package net.nothingtv.game.network.server;

import net.nothingtv.game.network.MessageHandler;
import net.nothingtv.game.network.NetworkReader;
import net.nothingtv.game.network.NetworkWriter;
import net.nothingtv.game.network.Tools;
import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.MessageRegister;
import net.nothingtv.game.network.message.Messages;
import net.nothingtv.game.network.message.impl.*;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This session handler controls the login process of the user and is started by the login server
 */
public class LoginSession implements Runnable, MessageHandler {

    private static final Logger LOG = Logger.getLogger(LoginSession.class.getName());
    private final SocketChannel socket;
    private final LoginServer loginServer;
    private NetworkWriter writer;
    private boolean loggedIn;
    private boolean waitForGS;
    private String login;
    private int token;
    private long userAnnouncedTime;

    public LoginSession(LoginServer loginServer, SocketChannel socket) {
        this.loginServer = loginServer;
        this.socket = socket;
        loggedIn = false;
        waitForGS = false;
    }

    @Override
    public void run() {
        LOG.info("LS: Login session started on " + (socket.isBlocking() ? "" : "non-") + "blocking channel.");
        Thread.currentThread().setName("LS user session");
        new Thread(new NetworkReader(socket, this)).start();
        new Thread(writer = new NetworkWriter(socket)).start();
        LoginServerGreeting greetings = Messages.obtain(LoginServerGreeting.class);
        greetings.version = LoginServer.Version;
        writer.send(greetings);

        long started = System.currentTimeMillis();
        while (socket.isConnected()) {
            Tools.nap(100);
            if (loggedIn && waitForGS) {
                if (loginServer.isExpectedUser(login)) {
                    GameServerToken reply = Messages.obtain(GameServerToken.class);
                    reply.token = token;
                    writer.send(reply);
                    waitForGS = false;
                } else {
                    if (System.currentTimeMillis() - userAnnouncedTime > 5000) {
                        LOG.log(Level.WARNING, "LS got no response from GS to user announce");
                        break;
                    }
                }
            }
            if (System.currentTimeMillis() - started > 30000) {
                LOG.log(Level.INFO, "LS: Login session timed out");
                break;
            }
        }
        try {
            socket.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "LS could not close user session socket");
        }
        writer.stop();
        LOG.info("LS: Login session stopped");
    }

    public void handleMessage(Message message) {
        MessageRegister.MessageId id = MessageRegister.MessageId.values[message.getMessageId()];
        switch(id) {
            case LoginRequest -> {
                LoginRequest lr = (LoginRequest) message;
                LoginReply reply = Messages.obtain(LoginReply.class);
                if (loginServer.authenticate(lr.login, lr.password)) {
                    login = lr.login;
                    loggedIn = true;
                    reply.accepted = true;
                    reply.servers = loginServer.getGameServers();
                } else {
                    reply.accepted = false;
                    reply.message = "Authentication failed.";
                    loggedIn = false;
                }
                writer.send(reply);
            }
            case ChooseServerRequest -> {
                if (loggedIn) {
                    ChooseServerRequest csr = (ChooseServerRequest) message;
                    AnnounceUser au = Messages.obtain(AnnounceUser.class);
                    au.login = login;
                    token = loginServer.createUserToken(login);
                    au.token = token;
                    loginServer.sendToGS(csr.serverNumber, au);
                    waitForGS = true;
                    userAnnouncedTime = System.currentTimeMillis();
                } else LOG.warning("LS: Got a ChooseServerRequest from a client without a login");
            }
            default -> LOG.info("Unhandled message on LS " + message.getClass().getName());
        }
        Messages.releaseMessage(message);
    }
}
