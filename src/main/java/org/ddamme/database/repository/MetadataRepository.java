package org.ddamme.database.repository;

import org.ddamme.database.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetadataRepository extends JpaRepository<FileMetadata, Long> {
} 