package com.ugcleague.ops.repository;

import com.ugcleague.ops.domain.GameServer;
import com.ugcleague.ops.domain.RemoteFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RemoteFileRepository extends JpaRepository<RemoteFile, Long> {

    Optional<RemoteFile> findByFolderAndFilenameAndOwner(String folder, String filename, GameServer owner);

    List<RemoteFile> findByFolderAndOwner(String folder, GameServer owner);
}
