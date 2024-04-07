package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class LoginRequest extends Message {

    public String login;
    public String password;

    @Override
    public short getMessageId() {
        return 2;
    }

    @Override
    public void released() {
        login = "";
        password = "";
    }

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        login = getString(buffer);
        password = getString(buffer);
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        putString(buffer, login);
        putString(buffer, password);
    }

}
