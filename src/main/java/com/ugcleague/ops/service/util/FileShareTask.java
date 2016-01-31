package com.ugcleague.ops.service.util;

import com.ugcleague.ops.domain.RemoteFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileShareTask {

    private final List<RemoteFile> requested = new ArrayList<>();
    private final List<RemoteFile> successful = new ArrayList<>();
    private Optional<String> batchSharedUrl = Optional.empty();
    private long start = System.currentTimeMillis();
    private long end;

    public List<RemoteFile> getRequested() {
        return requested;
    }

    public List<RemoteFile> getSuccessful() {
        return successful;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public Optional<String> getBatchSharedUrl() {
        return batchSharedUrl;
    }

    public void setBatchSharedUrl(Optional<String> batchSharedUrl) {
        this.batchSharedUrl = batchSharedUrl;
    }
}
