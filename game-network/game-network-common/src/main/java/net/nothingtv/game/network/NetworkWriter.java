package net.nothingtv.game.network;

import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.Messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.channels.SocketChannel;

public class NetworkWriter implements Runnable {

    private static final Logger LOG = Logger.getLogger(NetworkWriter.class.getName());
    private final SocketChannel channel;
    private static final int writeBufferSize = 2000;
    private final ByteBuffer writeBuffer;
    private final ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();

    public NetworkWriter(SocketChannel channel) {
        this.channel = channel;
        writeBuffer = ByteBuffer.allocateDirect(writeBufferSize);
    }

    public void send(Message message) {
        synchronized(messages) {
            messages.add(message);
            messages.notify();
        }
    }

    @Override
    public void run() {
        LOG.info("Channel writer started");
        Message message = null;
        boolean tryAgain = false;
        while (channel.isOpen()) {
            try {
                if (messages.isEmpty() && writeBuffer.position() == 0) {
                    // nothing to do - wait for new messages
                    synchronized (messages) {
                        try {
                            messages.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                while (!messages.isEmpty()) {
                    if (!tryAgain)
                        message = messages.poll();
                    if (message != null) {
                        int pos = writeBuffer.position();
                        if (!message.write(writeBuffer)) {
                            writeBuffer.position(pos);
                            if (writeBuffer.position() > 0) {
                                tryAgain = true;
                                break;
                            }
                            LOG.log(Level.WARNING, "Cannot write message " + message);
                            tryAgain = false;
                        } else {
                            LOG.info("Message " + message.getClass() + " sent.");
                            Messages.releaseMessage(message);
                            tryAgain = false;
                        }
                        if (writeBuffer.remaining() < 500) {
                            break;
                        }
                    }
                }
                if (writeBuffer.position() != 0) {
                    writeBuffer.flip();
                    channel.write(writeBuffer);
                    writeBuffer.clear();
                }
            } catch (AsynchronousCloseException ex) {
                break;
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Network error " + ex.getMessage(), ex);
                try {
                    channel.shutdownOutput();
                    break;
                } catch (IOException e) {
                    LOG.log(Level.INFO, "Network error " + e.getMessage(), e);
                }
            }
        }
        LOG.info("Channel writer stopped");
    }
}
