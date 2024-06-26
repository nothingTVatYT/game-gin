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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.*;

public class Server {
    private static final Logger LOG = Logger.getLogger(Server.class.getName());
    protected static final byte[] ID_KEY = "__id__".getBytes(StandardCharsets.UTF_8);
    protected static final byte[] ERROR_VAL = "ERROR".getBytes(StandardCharsets.UTF_8);

    private final StorageConfig config;
    private volatile boolean active;
    private ServerSocketChannel serverChannel;
    private static Server serverInstance;
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
            LOG.log(Level.INFO, "Storage Server Session started");
            try {
                while (channel.isConnected()) {
                    int r = channel.read(buffer);
                    if (r < 0) break;
                    if (r > 0) {
                        readRequest(buffer);
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Storage Server Session", e);
            }
            LOG.log(Level.INFO, "Storage Server Session stopped");
        }

        void readRequest(ByteBuffer buffer) throws IOException {
            int pos = buffer.position();
            buffer.flip();
            outbound.clear();
            while (buffer.hasRemaining()) {
                request.command = buffer.get();
                request.requestLength = buffer.getShort();
                if (request.requestLength <= 0) {
                    LOG.log(Level.WARNING, "invalid request length " + request.requestLength + " for cmd" + (int)request.command);
                }
                if (buffer.remaining() < request.requestLength || request.requestLength <= 0) {
                    // package is incomplete
                    buffer.position(pos);
                    buffer.limit(buffer.capacity());
                    LOG.log(Level.INFO, "reading incomplete.");
                    if (outbound.position() > 0) {
                        outbound.flip();
                        channel.write(outbound);
                    }
                    return;
                }
                int fl = buffer.get();
                ColumnFamilyHandle handle;
                if (fl > 0) {
                    buffer.get(tmp, 0, fl);
                    String columnFamily = new String(tmp, 0, fl, StandardCharsets.UTF_8);
                    handle = columns.get(columnFamily);
                    if (handle == null) {
                        LOG.log(Level.WARNING, "Unknown column family name " + columnFamily);
                    }
                } else {
                    handle = db.getDefaultColumnFamily();
                }
                // expect a key unless it's an insert or uid
                int kl = 0;
                if (request.command != Storage.INSERT && request.command != Storage.UID) {
                    kl = buffer.get();
                    buffer.get(tmp, 0, kl);
                }
                switch (request.command) {
                    case Storage.GET -> {
                        int r = -1;
                        try {
                            r = db.get(handle, tmp, 0, kl, valueBuffer, 0, valueBuffer.length);
                        } catch (RocksDBException re) {
                            LOG.log(Level.WARNING, "in get(" + new String(tmp, 0, kl, StandardCharsets.UTF_8) + ")", re);
                        }
                        if (r == RocksDB.NOT_FOUND) {
                            outbound.putShort((short) -1);
                        } else {
                            outbound.putShort((short) r);
                            outbound.put(valueBuffer, 0, r);
                        }
                    }
                    case Storage.UID -> {
                        try {
                            byte[] id = getNewId(db);
                            outbound.putShort((short) id.length);
                            outbound.put(id);
                        } catch (RocksDBException re) {
                            LOG.log(Level.WARNING, "in uid", re);
                            outbound.putShort((short) ERROR_VAL.length);
                            outbound.put(ERROR_VAL);
                        }
                    }
                    case Storage.INSERT -> {
                        try {
                            byte[] id = getNewId(db);
                            int vl = buffer.getShort();
                            buffer.get(valueBuffer, 0, vl);
                            db.put(handle, id, 0, 4, valueBuffer, 0, vl);
                            outbound.putShort((short) id.length);
                            outbound.put(id);
                        } catch (RocksDBException re) {
                            LOG.log(Level.WARNING, "in insert(" + new String(tmp, 0, kl, StandardCharsets.UTF_8) + ")", re);
                            outbound.putShort((short) ERROR_VAL.length);
                            outbound.put(ERROR_VAL);
                        }
                    }
                    case Storage.UPDATE -> {
                        int vl = 0;
                        try {
                            vl = buffer.getShort();
                            buffer.get(valueBuffer, 0, vl);
                            db.put(handle, tmp, 0, kl, valueBuffer, 0, vl);
                            outbound.putShort((short) 0);
                        } catch (Exception re) {
                            LOG.log(Level.WARNING, "in update(" + Storage.toHex(tmp, 0, kl) + ", "
                                    + new String(valueBuffer, 0, vl, StandardCharsets.UTF_8)+ ")", re);
                            outbound.putShort((short) ERROR_VAL.length);
                            outbound.put(ERROR_VAL);
                        }
                    }
                    case Storage.DELETE -> {
                        try {
                            db.delete(handle, tmp, 0, kl);
                            outbound.putShort((short) 0);
                        } catch (RocksDBException re) {
                            LOG.log(Level.WARNING, "in delete(" + new String(tmp, 0, kl, StandardCharsets.UTF_8) + ")", re);
                            outbound.putShort((short) ERROR_VAL.length);
                            outbound.put(ERROR_VAL);
                        }
                    }
                    case (byte)0xee -> new Thread(() -> serverInstance.scheduleShutdown()).start();
                }
            }
            outbound.flip();
            channel.write(outbound);
            buffer.clear();
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
        try {
            Path dir = Paths.get("log");
            if (!Files.isDirectory(dir))
                Files.createDirectory(dir);
            Handler handler = new FileHandler("log/storageserver.log");
            handler.setFormatter(new SimpleFormatter());
            Logger.getGlobal().setLevel(Level.INFO);
            LOG.addHandler(handler);
        } catch (Exception e) {
            System.err.println("Failed to setup logging." + e);
        }
        this.config = config;
        serverInstance = this;
    }

    private void scheduleShutdown() {
        new Thread(() -> {
            try {
                Thread.sleep(2500);
            } catch (Exception e) {
            }
            shutdown();
        }).start();
    }

    public void shutdown() {
        active = false;
        try {
            serverChannel.close();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not close server channel", e);
        }
    }

    public static void shutdownInstance(StorageConfig config) {
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(new InetSocketAddress(config.serverPort));
            ByteBuffer buffer = ByteBuffer.allocateDirect(8);
            buffer.put((byte)0xee);
            buffer.putShort((short)2);
            buffer.put((byte)0);
            buffer.put((byte)0);
            buffer.flip();
            channel.write(buffer);
        } catch (IOException e) {
            System.err.println("Cannot connect to server instance" + e);
        }
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
        //Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

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
                // test write
                byte[] key1 = "startTime".getBytes(StandardCharsets.UTF_8);
                byte[] value1 = String.format("%d", System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
                db.put(columnFamilyHandleList.getFirst(), key1, 0, key1.length, value1, 0, value1.length);

                try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
                    serverChannel = serverSocket;
                    serverSocket.configureBlocking(true);
                    serverSocket.bind(new InetSocketAddress(config.serverPort));
                    while (active) {
                        new Thread(new ServerSession(serverSocket.accept(), db)).start();
                    }
                } catch (ClosedChannelException e) {
                    LOG.log(Level.INFO, "Channel closed - exiting");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    db.flushWal(true);

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
        if (args.length > 0 && "shutdown".equals(args[0])) {
            Server.shutdownInstance(new StorageConfig());
        } else {
            StorageConfig config = new StorageConfig();
            new Server(config).run();
        }
    }
}
