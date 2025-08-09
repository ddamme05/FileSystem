package org.ddamme.controller;

import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.dto.PagedFileResponse;
import org.ddamme.service.MetadataService;
import org.ddamme.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class FileControllerTest {

    @Test
    @DisplayName("getUserFiles returns paged list for current user")
    void listFiles_returnsPaged() {
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        StorageService storageService = Mockito.mock(StorageService.class);
        FileController controller = new FileController(metadataService, storageService);

        User current = User.builder().id(7L).username("alice").email("e").password("p").build();
        FileMetadata fm = FileMetadata.builder()
                .id(1L)
                .originalFilename("f.txt")
                .storageKey("k")
                .size(3L)
                .contentType("text/plain")
                .uploadTimestamp(Instant.now())
                .build();
        Page<FileMetadata> page = new PageImpl<>(List.of(fm), PageRequest.of(0, 20), 1);
        when(metadataService.findByUser(eq(current), any(Pageable.class))).thenReturn(page);

        ResponseEntity<PagedFileResponse> response = controller.getUserFiles(current, 0, 20);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFiles()).hasSize(1);
        assertThat(response.getBody().getFiles().get(0).getId()).isEqualTo(1L);
    }
}
