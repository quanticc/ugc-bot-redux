package com.ugcleague.ops.service.util;

import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class SimpleTransferListener implements FTPDataTransferListener {

    private static final Logger log = LoggerFactory.getLogger(SimpleTransferListener.class);
    private long total = 0L;
    private long accumulated = 0L;
    private long start;
    private long last;
    private long lastDisplay;

    private static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private static String formatMillis(final long millis) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis));
        long hours = TimeUnit.MILLISECONDS.toHours(millis);

        StringBuilder b = new StringBuilder();
        b.append(hours == 0 ? "00" : hours < 10 ? String.valueOf("0" + hours) : String.valueOf(hours));
        b.append(":");
        b.append(minutes == 0 ? "00" : minutes < 10 ? String.valueOf("0" + minutes) : String.valueOf(minutes));
        b.append(":");
        b.append(seconds == 0 ? "00" : seconds < 10 ? String.valueOf("0" + seconds) : String.valueOf(seconds));
        return b.toString();
    }

    public void started() {
        log.info("Transfer started");
        start = System.currentTimeMillis();
        last = start;
        lastDisplay = start;
    }

    public void transferred(int length) {
        total += length;
        accumulated += length;
        last = System.currentTimeMillis();
        if (last - lastDisplay > 2000) {
            double elapsed = Math.max(0, last - lastDisplay);
            log.info("Transferred {} @ {}/s", humanReadableByteCount(total, true),
                humanReadableByteCount((long) (accumulated / (elapsed / 1000)), true));
            lastDisplay = last;
            accumulated = 0L;
        }
    }

    public void completed() {
        double elapsed = Math.max(0, System.currentTimeMillis() - start);
        log.info("Transfer completed: {} in {} @ {}/s", humanReadableByteCount(total, true), formatMillis((long) elapsed),
            humanReadableByteCount((long) (total / (elapsed / 1000)), true));
    }

    public void aborted() {
        log.warn("Transfer aborted");
    }

    public void failed() {
        log.warn("Transfer failed");
    }

}
