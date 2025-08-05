package org.ddamme.service;

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
    public FileMetadata save(FileMetadata metadata) {
        return metadataRepository.save(metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public FileMetadata findById(Long id) {
        return metadataRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FileMetadata", "id", id));
    }

    @Override
    public void deleteById(Long id) {
        metadataRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileMetadata> findByUser(User user) {
        return metadataRepository.findByUserOrderByUploadTimestampDesc(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FileMetadata> findByUser(User user, Pageable pageable) {
        return metadataRepository.findByUserOrderByUploadTimestampDesc(user, pageable);
    }
} 