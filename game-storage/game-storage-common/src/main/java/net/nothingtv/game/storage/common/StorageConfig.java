package net.nothingtv.game.storage.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class StorageConfig {

    public List<String> columnFamilies = new ArrayList<>();
    public int serverPort;

    public StorageConfig() {
        init();
    }

    public void init() {
        Properties properties = new Properties();
        File configFile = new File("storage.config");
        if (configFile.exists()) {
            try (InputStream is = new FileInputStream(configFile)) {
                properties.load(is);
                int numberColumnFamilies = Integer.parseInt(properties.getProperty("numberColumnFamilies", "20"));
                for (int i = 0; i < numberColumnFamilies; i++) {
                    String cf = properties.getProperty("columnFamily" + i, "");
                    if (!cf.isBlank())
                        columnFamilies.add(cf);
                }
                serverPort = Integer.parseInt(properties.getProperty("serverPort", "4788"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("No storage config found in " + new File("storage.config").getAbsolutePath());
            serverPort = 4788;
        }
    }
}
