package org.ddamme.service;

import lombok.RequiredArgsConstructor;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.repository.MetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetadataService {

    private final MetadataRepository metadataRepository;

    @Transactional
    public FileMetadata save(FileMetadata metadata) {
        return metadataRepository.save(metadata);
    }

    @Transactional(readOnly = true)
    public Optional<FileMetadata> findById(Long id) {
        return metadataRepository.findById(id);
    }

    @Transactional
    public void deleteById(Long id) {
        metadataRepository.deleteById(id);
    }
} 