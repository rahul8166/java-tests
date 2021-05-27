package com.tests.utils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ResourceUtils {
    public static String readClasspathFile(String path) throws IOException {
        InputStream in = ResourceUtils.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new BufferedReader(
                new InputStreamReader(in, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        try {
            String content = ResourceUtils.readClasspathFile("classpathFile.txt");
            System.out.println("Classpath file content :\n " + content);
        } catch (Exception e) {

        }

    }
}
