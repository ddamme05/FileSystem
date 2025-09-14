package org.ddamme.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.MetadataRepository;
import org.ddamme.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MetadataServiceImpl implements MetadataService {

  private final MetadataRepository metadataRepository;

  @Override
  @Observed(name = "db.file_metadata.save")
  public FileMetadata save(FileMetadata metadata) {
    return metadataRepository.save(metadata);
  }

  @Override
  @Transactional(readOnly = true)
  @Observed(name = "db.file_metadata.findById")
  public FileMetadata findById(Long id) {
    return metadataRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("FileMetadata", "id", id));
  }

  @Override
  @Transactional(readOnly = true)
  @Observed(name = "service.metadata.findOwnedById")
  public FileMetadata findOwnedById(User owner, Long id) {
    // Single-query ownership check - cleaner and faster than load-then-check
    return metadataRepository
        .findByIdAndUserId(id, owner.getId())
        .orElseThrow(() -> new ResourceNotFoundException("FileMetadata", "id", id));
  }

  @Override
  @Observed(name = "db.file_metadata.deleteById")
  public void deleteById(Long id) {
    metadataRepository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  @Observed(name = "db.file_metadata.findByUser")
  public List<FileMetadata> findByUser(User user) {
    return metadataRepository.findByUserOrderByUploadTimestampDesc(user);
  }

  @Override
  @Transactional(readOnly = true)
  @Observed(name = "db.file_metadata.findByUserPaged")
  public Page<FileMetadata> findByUser(User user, Pageable pageable) {
    return metadataRepository.findByUserOrderByUploadTimestampDesc(user, pageable);
  }
}
