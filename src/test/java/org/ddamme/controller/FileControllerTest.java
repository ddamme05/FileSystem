package org.ddamme.controller;

import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.dto.PagedFileResponse;
import org.ddamme.service.FileService;
import org.ddamme.service.MetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    FileService fileService = Mockito.mock(FileService.class);
    MetadataService metadataService = Mockito.mock(MetadataService.class);
    FileController controller = new FileController(fileService, metadataService);

    User current = User.builder().id(7L).username("alice").email("e").password("p").build();
    FileMetadata fileMetadata =
        FileMetadata.builder()
            .id(1L)
            .originalFilename("f.txt")
            .storageKey("k")
            .size(3L)
            .contentType("text/plain")
            .uploadTimestamp(Instant.now())
            .build();
    Page<FileMetadata> page = new PageImpl<>(List.of(fileMetadata), PageRequest.of(0, 20), 1);
    when(metadataService.findByUser(eq(current), any(Pageable.class))).thenReturn(page);

    ResponseEntity<PagedFileResponse> response = controller.getUserFiles(current, 0, 20);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getFiles()).hasSize(1);
    assertThat(response.getBody().getFiles().getFirst().getId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("size is clamped to 100 and pageable is passed correctly")
  void clampPageSize_andBeyondLast() {
    FileService fs = Mockito.mock(FileService.class);
    MetadataService ms = Mockito.mock(MetadataService.class);
    FileController c = new FileController(fs, ms);

    User u = User.builder().id(7L).username("alice").email("e").password("p").build();

    // empty page for "beyond last"
    Page<FileMetadata> empty = Page.empty(PageRequest.of(9, 100)); // page index 9, size 100
    when(ms.findByUser(Mockito.eq(u), Mockito.any(Pageable.class))).thenReturn(empty);

    c.getUserFiles(u, 9, 1000);

    var captor = ArgumentCaptor.forClass(Pageable.class);
    Mockito.verify(ms).findByUser(Mockito.eq(u), captor.capture());
    Pageable p = captor.getValue();
    assertThat(p.getPageSize()).isEqualTo(100);
    assertThat(p.getPageNumber()).isEqualTo(9);
  }
}
