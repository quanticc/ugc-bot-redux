package com.ugcleague.ops.service.util;

import org.itadaki.bzip2.BZip2InputStream;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;

public class BZip2Decompressor {

    private BZip2Decompressor() {
    }

    public static File decompress(String fileName) throws IOException {
        return decompress(new File(fileName));
    }

    public static File decompress(File inputFile) throws IOException {
        if (!inputFile.exists() || !inputFile.canRead() || !inputFile.getName().endsWith(".bz2")) {
            throw new IOException("Cannot read file " + inputFile.getPath());
        }
        File outputFile = new File(inputFile.toString().substring(0, inputFile.toString().length() - 4));
        if (outputFile.exists()) {
            throw new FileAlreadyExistsException(outputFile.toString());
        }
        try (InputStream fileInputStream = new BufferedInputStream(new FileInputStream(inputFile));
             BZip2InputStream inputStream = new BZip2InputStream(fileInputStream, false);
             OutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(outputFile), 524288)) {

            byte[] decoded = new byte[524288];
            int bytesRead;
            while ((bytesRead = inputStream.read(decoded)) != -1) {
                fileOutputStream.write(decoded, 0, bytesRead);
            }
        }
        return outputFile;
    }

}
