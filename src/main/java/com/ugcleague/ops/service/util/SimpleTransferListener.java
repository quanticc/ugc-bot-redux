package com.ugcleague.ops.service.util;

import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static com.ugcleague.ops.util.DateUtil.formatHuman;
import static com.ugcleague.ops.util.Util.humanizeBytes;

public class SimpleTransferListener implements FTPDataTransferListener {

    private static final Logger log = LoggerFactory.getLogger(SimpleTransferListener.class);
    private long total = 0L;
    private long accumulated = 0L;
    private long start;
    private long last;
    private long lastDisplay;

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
            log.info("Transferred {} @ {}/s", humanizeBytes(total),
                humanizeBytes((long) (accumulated / (elapsed / 1000))));
            lastDisplay = last;
            accumulated = 0L;
        }
    }

    public void completed() {
        double elapsed = Math.max(0, System.currentTimeMillis() - start);
        log.info("Transfer completed: {} in {} @ {}/s", humanizeBytes(total),
            formatHuman(Duration.ofMillis((long) elapsed), true),
            humanizeBytes((long) (total / (elapsed / 1000))));
    }

    public void aborted() {
        log.warn("Transfer aborted");
    }

    public void failed() {
        log.warn("Transfer failed");
    }

}
