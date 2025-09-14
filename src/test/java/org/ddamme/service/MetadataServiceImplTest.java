package org.ddamme.service;

import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.ddamme.database.repository.MetadataRepository;
import org.ddamme.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataServiceImplTest {

  private MetadataRepository metadataRepository;
  private MetadataServiceImpl metadataService;

  @BeforeEach
  void setUp() {
    metadataRepository = Mockito.mock(MetadataRepository.class);
    metadataService = new MetadataServiceImpl(metadataRepository);
  }

  @Test
  @DisplayName("findById returns entity when present")
  void findById_found() {
    FileMetadata fm =
        FileMetadata.builder()
            .id(10L)
            .originalFilename("a.txt")
            .storageKey("k")
            .size(1L)
            .contentType("text/plain")
            .uploadTimestamp(Instant.now())
            .build();
    when(metadataRepository.findById(10L)).thenReturn(Optional.of(fm));

    FileMetadata result = metadataService.findById(10L);
    assertThat(result).isSameAs(fm);
  }

  @Test
  @DisplayName("findById throws when absent")
  void findById_notFound() {
    when(metadataRepository.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> metadataService.findById(99L))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("deleteById delegates to repository")
  void deleteById_delegates() {
    metadataService.deleteById(5L);
    verify(metadataRepository).deleteById(5L);
  }

  @Test
  @DisplayName("findByUser returns correctly sorted list")
  void findByUser_returnsCorrectlySortedList() {
    User user = User.builder().id(1L).username("u").email("u@e").password("p").build();
    List<FileMetadata> list =
        List.of(
            FileMetadata.builder()
                .id(2L)
                .originalFilename("b")
                .storageKey("b")
                .size(2L)
                .contentType("t")
                .uploadTimestamp(Instant.now())
                .build());
    when(metadataRepository.findByUserOrderByUploadTimestampDesc(user)).thenReturn(list);
    List<FileMetadata> resultList = metadataService.findByUser(user);
    assertThat(resultList).hasSize(1).containsExactlyElementsOf(list);
  }

  @Test
  @DisplayName("findByUser returns correctly paged results")
  void findByUser_returnsCorrectlyPagedResults() {
    User user = User.builder().id(1L).username("u").email("u@e").password("p").build();
    List<FileMetadata> list =
        List.of(
            FileMetadata.builder()
                .id(2L)
                .originalFilename("b")
                .storageKey("b")
                .size(2L)
                .contentType("t")
                .uploadTimestamp(Instant.now())
                .build());
    Pageable pageable = PageRequest.of(0, 10);
    Page<FileMetadata> page = new PageImpl<>(list, pageable, 1);
    when(metadataRepository.findByUserOrderByUploadTimestampDesc(user, pageable)).thenReturn(page);
    Page<FileMetadata> resultPage = metadataService.findByUser(user, pageable);
    assertThat(resultPage.getTotalElements()).isEqualTo(1);
    assertThat(resultPage.getContent()).containsExactlyElementsOf(list);
  }
}
