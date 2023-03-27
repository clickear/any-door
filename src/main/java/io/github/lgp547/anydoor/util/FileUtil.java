package io.github.lgp547.anydoor.util;

import arthas.VmTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class FileUtil {

    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    /**
     * @return tmp file absolute path
     */
    public static String copyChildFile(File file, String child) {
        if (StringUtils.endsWithIgnoreCase(file.getPath(), ".jar")) {
            try (JarFile jarFile = new JarFile(file)) {
                // 获取Jar包中所有的文件
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    // 如果该文件是需要提取的资源文件
                    if (entryName.equals(child)) {
                        return getTmpLibFile(jarFile.getInputStream(entry)).getAbsolutePath();
                    }
                }
            } catch (IOException e) {
                log.error("copy jar file child error", e);
            }
            return "";
        }
        if (file.isDirectory()) {
            file = new File(file.getAbsolutePath(), child);
        }
        try {
            return getTmpLibFile(Files.newInputStream(file.toPath())).getAbsolutePath();
        } catch (IOException e) {
            log.error("copy file child error", e);
        }
        return "";
    }

    public static File getTmpLibFile(InputStream inputStream) throws IOException {
        File tmpLibFile = File.createTempFile(VmTool.JNI_LIBRARY_NAME, null);
        FileOutputStream tmpLibOutputStream = new FileOutputStream(tmpLibFile);
        copy(inputStream, tmpLibOutputStream);
        return tmpLibFile;
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }
}