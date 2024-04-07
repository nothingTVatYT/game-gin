package net.nothingtv.game.network.message;

import net.nothingtv.game.network.NVector3;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public abstract class Message {
    public static byte TRUE = (byte)1;
    public static byte FALSE = (byte)0;

    private byte[] tmp;

    public abstract void readContent(ByteBuffer buffer, short remaining);
    public abstract void writeContent(ByteBuffer buffer);
    public abstract short getMessageId();

    /**
     * clear data of this message before it's put into the pool
     */
    public abstract void released();

    public boolean read(ByteBuffer buffer, short remaining) {
        try {
            readContent(buffer, remaining);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean write(ByteBuffer buffer) {
        try {
            putMessageType(buffer);
            int pos = buffer.position();
            buffer.putShort((short)0);
            writeContent(buffer);
            int payloadLength = buffer.position() - pos - 2;
            buffer.putShort(pos, (short)payloadLength);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    protected void putMessageType(ByteBuffer buffer) {
        buffer.putShort(getMessageId());
    }

    protected void putString(ByteBuffer buffer, String text) {
        if (text == null || text.isEmpty()) {
            buffer.putShort((short)0);
            return;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short)bytes.length);
        buffer.put(bytes);
    }

    protected boolean getBoolean(ByteBuffer buffer) {
        return buffer.get() == TRUE;
    }

    protected void putBoolean(ByteBuffer buffer, boolean what) {
        buffer.put(what ? TRUE : FALSE);
    }

    protected String getString(ByteBuffer buffer) {
        short length = buffer.getShort();
        if (tmp == null || tmp.length < length)
            tmp = new byte[length];
        buffer.get(tmp, 0, length);
        return new String(tmp, 0, length, StandardCharsets.UTF_8);
    }

    protected String[] getStringArray(ByteBuffer buffer) {
        short size = buffer.getShort();
        String[] arr = new String[size];
        for (int i = 0; i < size; i++)
            arr[i] = getString(buffer);
        return arr;
    }

    protected void putStringArray(ByteBuffer buffer, String[] arr) {
        buffer.putShort((short)arr.length);
        for (String s : arr)
            putString(buffer, s);
    }

    protected void getVector3(ByteBuffer buffer, NVector3 vector) {
        vector.x = buffer.getFloat();
        vector.y = buffer.getFloat();
        vector.z = buffer.getFloat();
    }

    protected void putVector3(ByteBuffer buffer, NVector3 vector) {
        buffer.putFloat(vector.x);
        buffer.putFloat(vector.y);
        buffer.putFloat(vector.z);
    }
}
