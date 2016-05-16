package com.ugcleague.ops.repository.mongo;

import com.ugcleague.ops.domain.document.RemoteFile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RemoteFileRepository extends MongoRepository<RemoteFile, String> {

    Optional<RemoteFile> findByServerAndFolderAndFilename(String server, String folder, String filename);
}
