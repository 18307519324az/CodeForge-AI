package com.codeforge.ai.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PublicationViewDedupeEntityMapper {

    @Insert("""
            INSERT IGNORE INTO publication_view_dedupe
                (publication_id, viewer_key_hash, last_counted_at, created_at, updated_at)
            VALUES
                (#{publicationId}, #{viewerKeyHash}, #{lastCountedAt}, #{lastCountedAt}, #{lastCountedAt})
            """)
    int insertIgnore(@Param("publicationId") Long publicationId,
                     @Param("viewerKeyHash") String viewerKeyHash,
                     @Param("lastCountedAt") LocalDateTime lastCountedAt);

    @Update("""
            UPDATE publication_view_dedupe
            SET last_counted_at = #{lastCountedAt},
                updated_at = #{lastCountedAt}
            WHERE publication_id = #{publicationId}
              AND viewer_key_hash = #{viewerKeyHash}
              AND last_counted_at <= #{cutoff}
            """)
    int updateIfExpired(@Param("publicationId") Long publicationId,
                        @Param("viewerKeyHash") String viewerKeyHash,
                        @Param("cutoff") LocalDateTime cutoff,
                        @Param("lastCountedAt") LocalDateTime lastCountedAt);
}
