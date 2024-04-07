package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class AnnounceUser extends Message {

    public int token;
    public String login;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        token = buffer.getInt();
        login = getString(buffer);
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        buffer.putInt(token);
        putString(buffer, login);
    }

    @Override
    public short getMessageId() {
        return 6;
    }

    @Override
    public void released() {
        token = 0;
        login = "";
    }
}
