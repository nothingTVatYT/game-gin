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

    public void stop() {
        synchronized (messages) {
            messages.notify();
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("network writer");
        LOG.info("Channel writer started");
        Message message;
        while (channel.isConnected()) {
            try {
                if (messages.isEmpty() && writeBuffer.position() == 0) {
                    // nothing to do - wait for new messages
                    synchronized (messages) {
                        try {
                            messages.wait(10000);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                while (!messages.isEmpty()) {
                    int pos = writeBuffer.position();
                    message = messages.peek();
                    if (message != null) {
                        if (!message.write(writeBuffer)) {
                            writeBuffer.position(pos);
                            if (pos > 0) {
                                // try again next time the writebuffer is empty
                                break;
                            }
                            LOG.log(Level.WARNING, "Cannot write message " + message);
                            messages.poll();
                        } else {
                            messages.poll();
                            LOG.info("Message " + message.getClass() + " sent.");
                            Messages.releaseMessage(message);
                        }
                        if (writeBuffer.remaining() < 250) {
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
