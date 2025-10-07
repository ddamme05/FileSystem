package org.ddamme.service;

import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MetadataService {
    FileMetadata save(FileMetadata metadata);

    FileMetadata findById(Long id);

    FileMetadata findOwnedById(User owner, Long id);

    void deleteById(Long id);

    List<FileMetadata> findByUser(User user);

    Page<FileMetadata> findByUser(User user, Pageable pageable);
}
