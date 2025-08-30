package org.ddamme.testsupport;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.ddamme.exception.StorageOperationException;
import org.ddamme.service.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.multipart.MultipartFile;

@TestConfiguration
public class TestStorageConfig {

    @Bean
    @Primary
    public StorageService testStorageService() {
        return new InMemoryStorageService();
    }

    static class InMemoryStorageService implements StorageService {
        private final Set<String> keys = ConcurrentHashMap.newKeySet();

        @Override
        public String upload(MultipartFile file) {
            String key = UUID.randomUUID() + "-" + (file.getOriginalFilename()==null?"file":file.getOriginalFilename());
            keys.add(key); 
            return key;
        }

        @Override
        public String upload(MultipartFile file, String storageKey) {
            keys.add(storageKey); 
            return storageKey;
        }

        @Override
        public String generatePresignedDownloadUrl(String storageKey) {
            if (!keys.contains(storageKey)) throw new StorageOperationException("Unknown storage key: " + storageKey);
            return "http://localhost/fake/" + storageKey;
        }

        @Override
        public String generatePresignedDownloadUrl(String key, String originalName) {
            if (!keys.contains(key)) throw new StorageOperationException("Unknown storage key: " + key);
            return "http://localhost/fake/" + key;
        }

        @Override
        public void delete(String storageKey) { 
            keys.remove(storageKey); 
        }
    }
}


