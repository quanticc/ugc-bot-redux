package com.ugcleague.ops.service.discord;

import com.ugcleague.ops.service.ServerFileService;
import com.ugcleague.ops.service.SyncGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SyncQueryService {

    private static final Logger log = LoggerFactory.getLogger(SyncQueryService.class);

    private final CommandService commandService;
    private final SyncGroupService syncGroupService;
    private final ServerFileService serverFileService;

    @Autowired
    public SyncQueryService(CommandService commandService, SyncGroupService syncGroupService, ServerFileService serverFileService) {
        this.commandService = commandService;
        this.syncGroupService = syncGroupService;
        this.serverFileService = serverFileService;
    }

    @Autowired
    private void configure() {
        // .sync list --full
        initSyncListCommand();
        // .sync add --local <local_dir> --remote <remote_dir> --type <general|maps|cfg>
        initSyncAddCommand();
        // .sync edit --id <group_id> [--local <local_dir>] [--remote <remote_dir>] [--type <general|maps|cfg>]
        initSyncEditCommand();
        // .sync delete --id <group_id> [--force]
        initSyncDeleteCommand();
        // .sync refresh [--force]
        initSyncRefreshCommand();
        // .sync upload --all <non-option: servers>
        initSyncUploadCommand();
    }

    private void initSyncListCommand() {

    }

    private void initSyncAddCommand() {

    }

    private void initSyncEditCommand() {

    }

    private void initSyncDeleteCommand() {

    }

    private void initSyncRefreshCommand() {

    }

    private void initSyncUploadCommand() {

    }
}
