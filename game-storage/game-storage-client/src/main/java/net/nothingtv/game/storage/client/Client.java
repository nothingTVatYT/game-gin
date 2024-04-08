package net.nothingtv.game.storage.client;

import net.nothingtv.game.storage.common.Storage;
import net.nothingtv.game.storage.common.StorageConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client {

    private static final Logger LOG = Logger.getLogger(Client.class.getName());

    private SocketChannel channel;
    private final ByteBuffer buffer;

    public Client(StorageConfig config) {
        buffer = ByteBuffer.allocateDirect(32000);
        try {
            channel = SocketChannel.open();
            channel.connect(new InetSocketAddress("localhost", config.serverPort));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot open socket", e);
        }
    }

    public byte[] get(String columnFamily, byte[] key) throws IOException {
        synchronized(buffer) {
            buffer.clear();
            short requestLength;
            buffer.put(Storage.GET);
            buffer.putShort((short)0);
            byte[] cf = columnFamily.getBytes(StandardCharsets.UTF_8);
            buffer.put((byte)cf.length);
            buffer.put(cf);
            buffer.put((byte)key.length);
            buffer.put(key);
            requestLength = (short)(buffer.position() - 3);
            buffer.putShort(1, requestLength);
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
            channel.read(buffer);
            buffer.flip();
            short len = buffer.getShort();
            if (len < 0)
                return null;
            byte[] result = new byte[len];
            buffer.get(result);
            return result;
        }
    }

    public int uid() throws IOException {
        synchronized (buffer) {
            buffer.clear();
            buffer.put(Storage.UID);
            buffer.putShort((short)0);
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
            int r = channel.read(buffer);
            if (r < 6) {
                LOG.log(Level.WARNING, "short read");
                return -1;
            }
            buffer.flip();
            short idLength = buffer.getShort();
            int id;
            if (idLength == 4)
                id = buffer.getInt();
            else {
                id = 0;
                byte[] tmp = new byte[idLength];
                buffer.get(tmp);
                LOG.log(Level.WARNING, new String(tmp, 0, idLength, StandardCharsets.UTF_8) + " UID failed");
            }
            return id;
        }
    }

    public int insert(String columnFamily, byte[] value) throws IOException {
        synchronized(buffer) {
            buffer.clear();
            short requestLength;
            buffer.put(Storage.INSERT);
            buffer.putShort((short)0);
            byte[] cf = columnFamily.getBytes(StandardCharsets.UTF_8);
            buffer.put((byte)cf.length);
            buffer.put(cf);
            buffer.putShort((short)value.length);
            buffer.put(value);
            requestLength = (short)(buffer.position() - 3);
            buffer.putShort(1, requestLength);
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
            int r = channel.read(buffer);
            if (r < 6) {
                LOG.log(Level.WARNING, "short read");
            }
            buffer.flip();
            short idLength = buffer.getShort();
            int id;
            if (idLength == 4)
                id = buffer.getInt();
            else {
                id = 0;
                byte[] tmp = new byte[idLength];
                buffer.get(tmp);
                LOG.log(Level.WARNING, new String(tmp, 0, idLength, StandardCharsets.UTF_8) + " Insert failed");
            }
            return id;
        }
    }

    public void update(String columnFamily, byte[] key, byte[] value) throws IOException {
        synchronized(buffer) {
            buffer.clear();
            short requestLength;
            buffer.put(Storage.UPDATE);
            buffer.putShort((short)0);
            byte[] cf = columnFamily.getBytes(StandardCharsets.UTF_8);
            buffer.put((byte)cf.length);
            buffer.put(cf);
            buffer.put((byte)key.length);
            buffer.put(key);
            buffer.putShort((short)value.length);
            buffer.put(value);
            requestLength = (short)(buffer.position() - 3);
            buffer.putShort(1, requestLength);
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
            channel.read(buffer);
            buffer.flip();
            short respLength = buffer.getShort();
            if (respLength != 0) {
                byte[] tmp = new byte[respLength];
                buffer.get(tmp);
                LOG.log(Level.WARNING, new String(tmp, StandardCharsets.UTF_8) + " Update failed");
            }
        }
    }

    public void delete(String columnFamily, byte[] key) throws IOException {
        synchronized(buffer) {
            buffer.clear();
            short requestLength;
            buffer.put(Storage.UPDATE);
            buffer.putShort((short)0);
            byte[] cf = columnFamily.getBytes(StandardCharsets.UTF_8);
            buffer.put((byte)cf.length);
            buffer.put(cf);
            buffer.put((byte)key.length);
            buffer.put(key);
            requestLength = (short)(buffer.position() - 3);
            buffer.putShort(1, requestLength);
            buffer.flip();
            channel.write(buffer);
            buffer.clear();
            channel.read(buffer);
            buffer.flip();
            short respLength = buffer.getShort();
            if (respLength != 0) {
                byte[] tmp = new byte[respLength];
                buffer.get(tmp);
                LOG.log(Level.WARNING, new String(tmp, StandardCharsets.UTF_8) + " Delete failed");
            }
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot close socket", e);
        }
    }

    public static void main(String[] args) {
        Client client = new Client(new StorageConfig());
        try {
            int id = client.insert("default", "example content".getBytes(StandardCharsets.UTF_8));
            System.out.printf("insert got the id %d%n", id);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        client.close();
    }
}
