package com.example.litingting.cameraandclipimage.utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * Created by wzxx on 16/8/15.
 *
 * @author wzxx
 * @version 1.0
 * @since 2016-08-15
 */
public class IOUtils {

    public static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean deleteFile(String path) {
        File file = new File(path);
        return file.exists() && file.isFile() && file.delete();
    }
}
