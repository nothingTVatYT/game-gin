package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class LoginReply extends Message {

    public boolean accepted;
    public String message;
    public String servers;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        accepted = buffer.get() == TRUE;
        if (accepted)
            servers = getString(buffer);
        else message = getString(buffer);
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        if (accepted) {
            buffer.put(TRUE);
            putString(buffer, servers);
        } else {
            buffer.put(FALSE);
            putString(buffer, message);
        }
    }

    @Override
    public short getMessageId() {
        return 3;
    }

    @Override
    public void released() {
        message = "";
        servers = "";
    }
}
