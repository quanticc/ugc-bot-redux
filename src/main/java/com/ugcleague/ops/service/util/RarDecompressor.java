package com.ugcleague.ops.service.util;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Utility class to extract a RAR archive to a given location.
 *
 * @author Edmund Wagner
 */
public class RarDecompressor {

    private RarDecompressor() {
    }

    public static void decompress(String archive, String destination) throws IOException {
        if (archive == null || destination == null) {
            throw new IllegalArgumentException("archive and destination must be set");
        }
        File arch = new File(archive);
        if (!arch.exists()) {
            throw new IllegalArgumentException("the archive does not exit: " + archive);
        }
        File dest = new File(destination);
        if (!dest.exists() || !dest.isDirectory()) {
            throw new IllegalArgumentException("the destination must exist and point to a directory: " + destination);
        }
        decompress(arch, dest);
    }

    public static void decompress(File archive, File destination) throws IOException {
        try (Archive arch = new Archive(archive)) {
            if (arch.isEncrypted()) {
                throw new IOException("Archive is encrypted, cannot extract");
            }
            FileHeader fh = null;
            while (true) {
                fh = arch.nextFileHeader();
                if (fh == null) {
                    break;
                }
                if (fh.isEncrypted()) {
                    // it is possible to skip the file instead of aborting the process
                    throw new IOException("File is encrypted, cannot extract: " + fh.getFileNameString());
                }
                try {
                    if (fh.isDirectory()) {
                        createDirectory(fh, destination);
                    } else {
                        File f = createFile(fh, destination);
                        OutputStream stream = new FileOutputStream(f);
                        arch.extractFile(fh, stream);
                        stream.close();
                    }
                } catch (RarException e) {
                    throw new IOException("Error while extracting " + fh.getFileNameString() + ": " + e.getType().toString(), e);
                }
            }
        } catch (RarException e) {
            throw new IOException("Cannot create RAR archive: " + e.getType().toString(), e);
        }
    }

    private static File createFile(FileHeader fh, File destination) throws IOException {
        File f = null;
        String name = null;
        if (fh.isFileHeader() && fh.isUnicode()) {
            name = fh.getFileNameW();
        } else {
            name = fh.getFileNameString();
        }
        f = new File(destination, name);
        if (!f.exists()) {
            f = makeFile(destination, name);
        }
        return f;
    }

    private static File makeFile(File destination, String name) throws IOException {
        String[] dirs = name.split("\\\\");
        if (dirs == null) {
            return null;
        }
        String path = "";
        int size = dirs.length;
        if (size == 1) {
            return new File(destination, name);
        } else if (size > 1) {
            for (int i = 0; i < dirs.length - 1; i++) {
                path = path + File.separator + dirs[i];
                new File(destination, path).mkdir();
            }
            path = path + File.separator + dirs[dirs.length - 1];
            File f = new File(destination, path);
            f.createNewFile();
            return f;
        } else {
            return null;
        }
    }

    private static void createDirectory(FileHeader fh, File destination) {
        File f = null;
        if (fh.isDirectory() && fh.isUnicode()) {
            f = new File(destination, fh.getFileNameW());
            if (!f.exists()) {
                makeDirectory(destination, fh.getFileNameW());
            }
        } else if (fh.isDirectory() && !fh.isUnicode()) {
            f = new File(destination, fh.getFileNameString());
            if (!f.exists()) {
                makeDirectory(destination, fh.getFileNameString());
            }
        }
    }

    private static void makeDirectory(File destination, String fileName) {
        String[] dirs = fileName.split("\\\\");
        if (dirs == null) {
            return;
        }
        String path = "";
        for (String dir : dirs) {
            path = path + File.separator + dir;
            new File(destination, path).mkdir();
        }

    }
}
