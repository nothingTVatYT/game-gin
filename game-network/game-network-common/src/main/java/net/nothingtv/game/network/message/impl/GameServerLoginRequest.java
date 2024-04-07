package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class GameServerLoginRequest extends Message {
    public String login;
    public int token;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        login = getString(buffer);
        token = buffer.getInt();
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        putString(buffer, login);
        buffer.putInt(token);
    }

    @Override
    public short getMessageId() {
        return 9;
    }

    @Override
    public void released() {
        login = "";
        token = 0;
    }
}
