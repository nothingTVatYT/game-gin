package net.nothingtv.game.network.message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CodeGen {

    public static void generateMessageRegister() {
        HashMap<Short, String> classNames = new HashMap<>();
        File cwd = new File("");
        String sourcePrefix = "src/main/java/";
        String cd = cwd.getAbsolutePath();
        System.out.printf("Working directory is %s, searching Message classes in %s/%s%n", cd, cd, sourcePrefix);
        String packageName = CodeGen.class.getPackageName() + ".impl";
        File basedir = new File(sourcePrefix + packageName.replace('.', '/'));
        if (basedir.exists() && basedir.isDirectory()) {
            int maxId = 0;
            for (String name : basedir.list()) {
                String className = name.replace(".java", "");
                try {
                    Class<?> c = Class.forName(packageName + "." + className);
                    Message msg = (Message)c.getConstructor().newInstance();
                    if (msg.getMessageId() < 1) {
                        System.err.printf("invalid id found: %s has id %d%n", className, msg.getMessageId());
                        throw new RuntimeException("invalid id found in message classes");
                    }
                    if (classNames.containsKey(msg.getMessageId())) {
                        System.err.printf("duplicate id found: %s and %s have %d%n",
                                classNames.get(msg.getMessageId()), className, msg.getMessageId());
                        throw new RuntimeException("duplicate id found in message classes");
                    }
                    classNames.put(msg.getMessageId(), className);
                    maxId = Math.max(maxId, msg.getMessageId());
                    System.out.printf("found %s with id %d%n", name, msg.getMessageId());
                } catch (ClassNotFoundException | InvocationTargetException | InstantiationException |
                         IllegalAccessException | NoSuchMethodException e) {
                    System.err.printf("Could not initialize class %s: %s%n", className, e);
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("package ").append(Message.class.getPackageName())
                    .append(";\n\n// THIS FILE IS GENERATED - DO NOT EDIT - RUN CodeGen.main()\n\nimport ")
                    .append(packageName).append(".*;\n\npublic class MessageRegister {\n    public enum MessageId {");

            for (int i = 0; i <= maxId; i++) {
                boolean knownId = classNames.containsKey((short)i);
                String className = (i == 0) ? "None" : knownId ? classNames.get((short)i) : ("Unknown" + i);
                sb.append("\n        ").append(className);
                if (i == maxId)
                    sb.append(';');
                else sb.append(',');
                sb.append(" //").append(i);
            }
            sb.append("\n\n        public static final MessageId[] values = values();\n    }");
            sb.append("\n\n    public static void register() {");
            for (Map.Entry<Short, String> entry : classNames.entrySet()) {
                sb.append("\n        Messages.registerMessage(").append(entry.getKey()).append(", ").append(entry.getValue()).append(".class);");
            }
            sb.append("\n    }\n}\n");
            File generatedFile = new File(basedir.getParentFile(), "MessageRegister.java");
            try (OutputStreamWriter os = new OutputStreamWriter(new FileOutputStream(generatedFile))) {
                os.write(sb.toString());
                System.out.printf("%s generated.%n", generatedFile);
            } catch (Exception ex) {
                System.err.println("Could not create file: " + ex);
            }
        }
    }

    public static void main(String[] args) {
        generateMessageRegister();
    }
}
