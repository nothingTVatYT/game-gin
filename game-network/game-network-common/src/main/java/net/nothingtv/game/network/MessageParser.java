package net.nothingtv.game.network;

import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.Messages;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageParser {

    private static final Logger LOG = Logger.getLogger(MessageParser.class.getName());

    ByteBuffer tmpBuffer = ByteBuffer.allocateDirect(2000);

    public void readMessages(ByteBuffer buffer, MessageHandler handler) {
        int pos = buffer.position();
        if (pos < Messages.MinSize)
            return;
        buffer.flip();
        while (buffer.hasRemaining()) {
            short messageType = buffer.getShort();
            short messageLength = buffer.getShort();
            if (buffer.remaining() >= messageLength) {
                Message message = Messages.obtain(messageType);
                if (message.read(buffer, messageLength))
                    handler.handleMessage(message);
                else LOG.log(Level.WARNING, "Cannot read message type=" + messageType + ", length=" + messageLength);
            } else {
                tmpBuffer.putShort(messageType);
                tmpBuffer.putShort(messageLength);
                tmpBuffer.put(buffer);
                tmpBuffer.flip();
                buffer.clear();
                buffer.put(tmpBuffer);
                break;
            }
            if (!buffer.hasRemaining()) {
                buffer.clear();
                break;
            }
        }
    }
}
