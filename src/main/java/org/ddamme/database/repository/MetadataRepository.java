package org.ddamme.database.repository;

import io.micrometer.observation.annotation.Observed;
import org.ddamme.database.model.FileMetadata;
import org.ddamme.database.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Observed(name = "repository.metadata")
public interface MetadataRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findByUserOrderByUploadTimestampDesc(User user);

    @Query("SELECT file FROM FileMetadata file WHERE file.user.id = :userId ORDER BY file.uploadTimestamp DESC")
    Page<FileMetadata> findByUserIdOrderByUploadTimestampDesc(Long userId, Pageable pageable);

    Optional<FileMetadata> findByIdAndUser_Id(Long id, Long userId);

    @Query("select coalesce(sum(f.size), 0) from FileMetadata f")
    long sumSizes();

    /**
     * Full-text search using PostgreSQL FTS with weighted ranking.
     * Returns: [id, filename, contentType, size, uploadedAt, rank, snippet, ocrConfidence]
     * 
     * DEPRECATED: Use searchTextWithCursor for pagination without OFFSET drift.
     */
    @Query(value = """
        SELECT
            f.id,
            f.original_filename,
            f.content_type,
            f.size,
            f.upload_timestamp,
            ts_rank(f.search_vector, websearch_to_tsquery('english', :query)) AS rank,
            ts_headline('english', COALESCE(f.file_text, ''), 
                        websearch_to_tsquery('english', :query),
                        'MaxWords=50, MinWords=25') AS snippet,
            f.ocr_confidence
        FROM file_metadata f
        WHERE f.user_id = :userId
          AND f.search_vector @@ websearch_to_tsquery('english', :query)
        ORDER BY rank DESC
        LIMIT 100
        """, nativeQuery = true)
    List<Object[]> searchTextRaw(@Param("userId") Long userId,
                                 @Param("query") String query);
    
    /**
     * Full-text search with keyset pagination (stable cursors, no OFFSET drift).
     * 
     * Uses (rank, id) as composite cursor for deterministic pagination.
     * Fetches limit + 1 to detect "hasMore" without separate count query.
     * 
     * Keyset filter logic:
     * - First page: lastRank=null, lastId=null → fetch top N+1
     * - Next pages: WHERE (rank < :lastRank) OR (rank = :lastRank AND id > :lastId)
     * 
     * Returns: [id, filename, contentType, size, uploadedAt, rank, snippet, ocrConfidence]
     * 
     * See: cursor_v2_rationale.md Section "Keyset Pagination" for rationale
     */
    @Query(value = """
        WITH q AS (SELECT websearch_to_tsquery('english', :query) AS query)
        SELECT
            f.id,
            f.original_filename,
            f.content_type,
            f.size,
            f.upload_timestamp,
            ts_rank_cd(ARRAY[0.0, 0.0, 0.35, 1.0], f.search_vector, q.query) AS rank,
            ts_headline('english', COALESCE(f.file_text, ''), q.query,
                        'StartSel=<mark>,StopSel=</mark>,MaxFragments=2,MinWords=8,MaxWords=25,ShortWord=3,HighlightAll=FALSE') AS snippet,
            f.ocr_confidence
        FROM file_metadata f, q
        WHERE f.user_id = :userId
          AND f.search_vector @@ q.query
          AND (:lastRank IS NULL OR 
               (ts_rank_cd(ARRAY[0.0, 0.0, 0.35, 1.0], f.search_vector, q.query) < :lastRank 
                OR (ts_rank_cd(ARRAY[0.0, 0.0, 0.35, 1.0], f.search_vector, q.query) = :lastRank AND f.id > :lastId)))
        ORDER BY ts_rank_cd(ARRAY[0.0, 0.0, 0.35, 1.0], f.search_vector, q.query) DESC, f.id ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> searchTextWithCursor(@Param("userId") Long userId,
                                        @Param("query") String query,
                                        @Param("lastRank") Double lastRank,
                                        @Param("lastId") Long lastId,
                                        @Param("limit") int limit);
}

