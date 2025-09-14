package org.ddamme.database.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file_metadata")
public class FileMetadata {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String originalFilename;

  @Column(nullable = false, unique = true)
  private String storageKey;

  @Column(nullable = false)
  private long size;

  @Column(nullable = false)
  private String contentType;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant uploadTimestamp;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updateTimestamp;

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    FileMetadata that = (FileMetadata) obj;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "FileMetadata{"
        + "id="
        + id
        + ", originalFilename='"
        + originalFilename
        + '\''
        + ", storageKey='"
        + storageKey
        + '\''
        + ", size="
        + size
        + ", contentType='"
        + contentType
        + '\''
        + '}';
  }
}
