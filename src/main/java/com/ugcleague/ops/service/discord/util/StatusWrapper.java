package com.ugcleague.ops.service.discord.util;

import org.apache.commons.lang3.StringUtils;

public class StatusWrapper {

    public static StatusWrapper ofWork(long workDone, long totalWork) {
        StatusWrapper s = new StatusWrapper();
        s.workDone = workDone;
        s.totalWork = totalWork;
        return s;
    }

    public static StatusWrapper ofPercent(double progress) {
        StatusWrapper s = new StatusWrapper();
        s.progress = progress;
        return s;
    }

    private long workDone = -1;
    private long totalWork = -1;
    private double progress = -1;
    private boolean displayingBar = false;
    private boolean displayingText = false;
    private String message = "";

    private StatusWrapper() {

    }

    public StatusWrapper withMessage(String message) {
        this.message = message;
        return this;
    }

    public StatusWrapper text() {
        this.displayingText = true;
        return this;
    }

    public StatusWrapper bar() {
        this.displayingBar = true;
        return this;
    }

    public long getWorkDone() {
        return workDone;
    }

    public long getTotalWork() {
        return totalWork;
    }

    public double getProgress() {
        return progress;
    }

    public boolean isDisplayingBar() {
        return displayingBar;
    }

    public boolean isDisplayingText() {
        return displayingText;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (displayingBar) {
            int percent = -1;
            if (progress == -1) {
                if (workDone >= 0 && totalWork > 0) {
                    percent = (int) Math.floor((double) workDone / totalWork * 100);
                }
            } else {
                percent = (int) Math.floor(progress);
            }
            if (percent >= 0) {
                int chars = percent / 10;
                result.append(StringUtils.repeat(":black_large_square:", chars))
                    .append(StringUtils.repeat(":white_large_square:", 10 - chars));
            }
        }
        if (displayingText) {
            if (progress >= 0 && (workDone < 0 || totalWork <= 0)) {
                result.append(String.format(" %.0f%%", progress));
            }
            if (progress == -1) {
                result.append(String.format(" %d/%d", workDone, totalWork));
            }
        }
        if (message != null && !message.isEmpty()) {
            result.append(" ").append(message);
        }
        return result.toString();
    }
}
