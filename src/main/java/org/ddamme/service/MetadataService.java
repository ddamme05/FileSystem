package org.ddamme.service;

import org.ddamme.database.model.FileMetadata;

public interface MetadataService {
    FileMetadata save(FileMetadata metadata);
    FileMetadata findById(Long id);
    void deleteById(Long id);
} 