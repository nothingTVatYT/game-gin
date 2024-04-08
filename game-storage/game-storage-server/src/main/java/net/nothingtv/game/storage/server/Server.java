package net.nothingtv.game.storage.server;

import net.nothingtv.game.storage.common.Storage;
import net.nothingtv.game.storage.common.StorageConfig;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    protected static final byte[] ID_KEY = "__id__".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] ERROR_VAL = "ERROR".getBytes(StandardCharsets.UTF_8);

    private final StorageConfig config;
    private volatile boolean active;
    static final HashMap<String, ColumnFamilyHandle> columns = new HashMap<>();

    static class StorageRequest {
        byte command;
        short requestLength;
    }
    static class ServerSession implements Runnable {
        SocketChannel channel;
        RocksDB db;
        ByteBuffer buffer;
        ByteBuffer outbound;
        byte[] tmp = new byte[256];
        byte[] valueBuffer = new byte[32000];
        final StorageRequest request = new StorageRequest();

        ServerSession(SocketChannel channel, RocksDB db) {
            this.channel = channel;
            this.db = db;
            buffer = ByteBuffer.allocateDirect(32000);
            outbound = ByteBuffer.allocateDirect(32000);
        }

        @Override
        public void run() {
            LOG.log(Level.INFO, "Storage Server Session");
            try {
                while (channel.isConnected()) {
                    int r = channel.read(buffer);
                    if (r > 0) {
                        readRequest(buffer);
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Storage Server Session", e);
            }
        }

        void readRequest(ByteBuffer buffer) throws IOException {
            int pos = buffer.position();
            buffer.flip();
            request.command = buffer.get();
            request.requestLength = buffer.getShort();
            if (pos < request.requestLength + 3) {
                // package is incomplete
                buffer.position(pos);
                return;
            }
            int fl = buffer.get();
            ColumnFamilyHandle handle;
            if (fl > 0) {
                buffer.get(tmp, 0, fl);
                String columnFamily = new String(tmp, 0, fl, StandardCharsets.UTF_8);
                handle = columns.get(columnFamily);
            } else {
                handle = db.getDefaultColumnFamily();
            }
            // expect a key unless it's an insert
            int kl = 0;
            if (request.command != Storage.INSERT) {
                kl = buffer.get();
                buffer.get(tmp, 0, kl);
            }
            outbound.clear();
            switch (request.command) {
                case Storage.GET -> {
                    int r = -1;
                    try {
                        r = db.get(handle, tmp, 0, kl, valueBuffer, 0, valueBuffer.length);
                    } catch (RocksDBException re) {
                        LOG.log(Level.WARNING, "in get(" + new String(tmp,0,kl, StandardCharsets.UTF_8) + ")", re);
                    }
                    if (r == RocksDB.NOT_FOUND) {
                        outbound.putShort((short)0);
                    } else {
                        outbound.putShort((short)r);
                        outbound.put(valueBuffer, 0, r);
                    }
                }
                case Storage.UID -> {
                    try {
                        byte[] id = getNewId(db);
                        outbound.putShort((short)id.length);
                        outbound.put(id);
                    } catch (RocksDBException re) {
                        LOG.log(Level.WARNING, "in uid", re);
                        outbound.putShort((short)ERROR_VAL.length);
                        outbound.put(ERROR_VAL);
                    }
                }
                case Storage.INSERT -> {
                    try {
                        byte[] id = getNewId(db);
                        int vl = buffer.getShort();
                        buffer.get(valueBuffer, 0, vl);
                        db.put(handle, id, 0, 4, valueBuffer, 0, vl);
                        outbound.putShort((short)id.length);
                        outbound.put(id);
                    } catch (RocksDBException re) {
                        LOG.log(Level.WARNING, "in insert(" + new String(tmp,0,kl, StandardCharsets.UTF_8) + ")", re);
                        outbound.putShort((short)ERROR_VAL.length);
                        outbound.put(ERROR_VAL);
                    }
                }
                case Storage.UPDATE -> {
                    try {
                        int vl = buffer.getShort();
                        buffer.get(valueBuffer, 0, vl);
                        db.put(handle, tmp, 0, kl, valueBuffer, 0, vl);
                        outbound.putShort((short)0);
                    } catch (RocksDBException re) {
                        LOG.log(Level.WARNING, "in update(" + new String(tmp,0,kl, StandardCharsets.UTF_8) + ")", re);
                        outbound.putShort((short)ERROR_VAL.length);
                        outbound.put(ERROR_VAL);
                    }
                }
                case Storage.DELETE -> {
                    try {
                        db.delete(handle, tmp, 0, kl);
                        outbound.putShort((short)0);
                    } catch (RocksDBException re) {
                        LOG.log(Level.WARNING, "in delete(" + new String(tmp,0,kl, StandardCharsets.UTF_8) + ")", re);
                        outbound.putShort((short)ERROR_VAL.length);
                        outbound.put(ERROR_VAL);
                    }
                }
            }
            outbound.flip();
            channel.write(outbound);
        }

        public byte[] getNewId(RocksDB db) throws RocksDBException {
            int id;
            byte[] tmp = new byte[4];
            int r = -1;
            try {
                r = db.get(ID_KEY, tmp);
            } catch (RocksDBException re) {
                LOG.log(Level.WARNING, "in get(__id__)", re);
            }
            if (r < 4) {
                id = 1;
            } else {
                id = tmp[0] << 24 | tmp[1] << 16 | tmp[2] << 8 | tmp[3] + 1;
            }
            tmp[3] = (byte)(id & 0xff);
            tmp[2] = (byte)(id >> 8 & 0xff);
            tmp[1] = (byte)(id >> 16 & 0xff);
            tmp[0] = (byte)(id >> 24 & 0xff);
            db.put(ID_KEY, tmp);
            return tmp;
        }

    }
    public Server(StorageConfig config) {
        this.config = config;
    }

    public void shutdown() {
        active = false;
    }

    public void start() {
        new Thread(this::run);
        Thread.yield();
    }

    public boolean isActive() {
        return active;
    }

    public void run() {
        active = true;
        LOG.log(Level.INFO, "Storage Server starting");
        // a static method that loads the RocksDB C++ library.
        RocksDB.loadLibrary();

        try (final ColumnFamilyOptions cfOpts = new ColumnFamilyOptions().optimizeUniversalStyleCompaction()) {

            // list of column family descriptors, first entry must always be default column family
            final List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
            ColumnFamilyDescriptor descriptor = new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOpts);
            cfDescriptors.add(descriptor);
            for (String cfName : config.columnFamilies) {
                descriptor = new ColumnFamilyDescriptor(cfName.getBytes(), cfOpts);
                cfDescriptors.add(descriptor);
            }

            // a list which will hold the handles for the column families once the db is opened
            final List<ColumnFamilyHandle> columnFamilyHandleList =
                    new ArrayList<>();

            try (final DBOptions options = new DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true);
                 final RocksDB db = RocksDB.open(options,
                         "storage/data", cfDescriptors,
                         columnFamilyHandleList)) {

                for (int i = 0; i < cfDescriptors.size(); i++) {
                    columns.put(new String(cfDescriptors.get(i).getName(), StandardCharsets.UTF_8), columnFamilyHandleList.get(i));
                }
                try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {

                    serverSocket.bind(new InetSocketAddress(config.serverPort));
                    while (active) {
                        new Thread(new ServerSession(serverSocket.accept(), db)).start();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {

                    // NOTE frees the column family handles before freeing the db
                    for (final ColumnFamilyHandle columnFamilyHandle :
                            columnFamilyHandleList) {
                        columnFamilyHandle.close();
                    }
                } // frees the db and the db options
            } catch (RocksDBException e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
            }
        } // frees the column family options
        LOG.log(Level.INFO, "Storage Server stopped");
    }

    public static void main(String[] args) {
        StorageConfig config = new StorageConfig();
        new Server(config).run();
    }
}
