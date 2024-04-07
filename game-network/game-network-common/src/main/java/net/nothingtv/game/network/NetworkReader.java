package net.nothingtv.game.network;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkReader implements Runnable {
    private static final Logger LOG = Logger.getLogger(NetworkReader.class.getName());

    private final SocketChannel channel;
    private final ByteBuffer readBuffer;
    private final MessageHandler messageHandler;
    private final MessageParser messageReader;
    private static final int readBufferSize = 2000;

    public NetworkReader(SocketChannel channel, MessageHandler handler) {
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocateDirect(readBufferSize);
        this.messageHandler = handler;
        messageReader = new MessageParser();
    }

    @Override
    public void run() {
        LOG.info("Channel reader started");
        while (channel.isOpen()) {
            try {
                int bytesRead = channel.read(readBuffer);
                if (bytesRead > 0) {
                    messageReader.readMessages(readBuffer, messageHandler);
                }
            } catch (AsynchronousCloseException ex) {
                break;
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Network error " + ex.getMessage(), ex);
                try {
                    channel.shutdownInput();
                    break;
                } catch (IOException e) {
                    LOG.log(Level.INFO, "Network error " + e.getMessage(), e);
                }
            }
        }
        LOG.info("Channel reader stopped");
    }
}
