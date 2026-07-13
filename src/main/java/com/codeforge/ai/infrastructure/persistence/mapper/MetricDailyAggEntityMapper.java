package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.metrics.entity.MetricDailyAggEntity;
import com.mybatisflex.core.BaseMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MetricDailyAggEntityMapper extends BaseMapper<MetricDailyAggEntity> {

    @Select("""
            SELECT id,
                   stat_date AS statDate,
                   metric_key AS metricKey,
                   metric_value AS metricValue,
                   dimensions_json AS dimensionsJson,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM metric_daily_agg
            WHERE stat_date = #{statDate}
            ORDER BY metric_key ASC, id ASC
            FOR UPDATE
            """)
    List<MetricDailyAggEntity> findByStatDateForUpdate(@Param("statDate") LocalDate statDate);

    @Update("""
            INSERT IGNORE INTO metric_daily_agg(stat_date, metric_key, metric_value, dimensions_json)
            VALUES (#{statDate}, #{metricKey}, #{metricValue}, NULL)
            """)
    int insertIgnoreMetricValue(@Param("statDate") LocalDate statDate,
                                @Param("metricKey") String metricKey,
                                @Param("metricValue") BigDecimal metricValue);

    @Select("""
            SELECT id,
                   stat_date AS statDate,
                   metric_key AS metricKey,
                   metric_value AS metricValue,
                   dimensions_json AS dimensionsJson,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM metric_daily_agg
            WHERE stat_date = (SELECT MAX(stat_date) FROM metric_daily_agg)
            ORDER BY metric_key ASC, id ASC
            """)
    List<MetricDailyAggEntity> findLatestSummaryMetrics();

    @Update("""
            INSERT INTO metric_daily_agg(stat_date, metric_key, metric_value, dimensions_json)
            VALUES (#{statDate}, #{metricKey}, #{metricValue}, NULL)
            ON DUPLICATE KEY UPDATE
                metric_value = #{metricValue},
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertMetricValue(@Param("statDate") LocalDate statDate,
                          @Param("metricKey") String metricKey,
                          @Param("metricValue") BigDecimal metricValue);

    @Select("""
            SELECT id,
                   stat_date AS statDate,
                   metric_key AS metricKey,
                   metric_value AS metricValue,
                   dimensions_json AS dimensionsJson,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM metric_daily_agg
            WHERE metric_key IN ('requestCount', 'successCount', 'failedCount', 'successRate',
                                 'tokenInput', 'tokenOutput', 'avgDurationMs')
            ORDER BY stat_date ASC, metric_key ASC, id ASC
            """)
    List<MetricDailyAggEntity> findAllSummaryMetrics();

    @Select("""
            SELECT MAX(updated_at)
            FROM metric_daily_agg
            """)
    LocalDateTime findLatestAggregationUpdatedAt();

    @Select("""
            SELECT MAX(stat_date)
            FROM metric_daily_agg
            """)
    LocalDate findLatestStatDate();
}
