package org.ddamme.database.repository;

import io.micrometer.observation.annotation.Observed;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Observed(name = "repository.metadata")
public interface MetadataRepository extends JpaRepository<FileMetadata, Long> {
  List<FileMetadata> findByUserOrderByUploadTimestampDesc(User user);

  Page<FileMetadata> findByUserOrderByUploadTimestampDesc(User user, Pageable pageable);

  Optional<FileMetadata> findByIdAndUserId(Long id, Long userId);
}
