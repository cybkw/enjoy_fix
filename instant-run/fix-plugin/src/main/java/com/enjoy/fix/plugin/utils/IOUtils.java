package com.enjoy.fix.plugin.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Lance
 * @date 2019/4/3
 */
public class IOUtils {


    public static void copy(InputStream is, OutputStream os) throws IOException {
        int len;
        byte[] buffer = new byte[4096];
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

}
