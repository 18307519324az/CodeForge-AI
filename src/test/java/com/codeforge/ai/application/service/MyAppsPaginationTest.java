package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AiAppQueryRequest;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MyAppsPaginationTest {

    private AiAppEntityMapper aiAppEntityMapper;
    private WorkspaceAccessService workspaceAccessService;
    private AppListSummaryAggregator appListSummaryAggregator;
    private AiAppApplicationService aiAppApplicationService;

    @BeforeEach
    void setUp() {
        aiAppEntityMapper = mock(AiAppEntityMapper.class);
        workspaceAccessService = mock(WorkspaceAccessService.class);
        appListSummaryAggregator = mock(AppListSummaryAggregator.class);
        given(appListSummaryAggregator.aggregate(any())).willReturn(java.util.Map.of());
        aiAppApplicationService = new AiAppApplicationService(
                aiAppEntityMapper, workspaceAccessService, appListSummaryAggregator,
                mock(AppPublicationApplicationService.class));
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
    }

    @Test
    void shouldQueryDbWithLimitOffsetForPageOne() {
        stubPage(List.of(app(3001L, "App 1")), 66L);

        AiAppQueryRequest request = query(1, 12);
        PageResponse<?> response = aiAppApplicationService.listApps(user(), request);

        verify(aiAppEntityMapper).countAccessibleApps(eq(List.of(1001L)), isNull(), isNull(), isNull());
        verify(aiAppEntityMapper).findAccessibleAppsPage(
                eq(List.of(1001L)), isNull(), isNull(), isNull(), eq(0L), eq(12L));
        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(12);
        assertThat(response.total()).isEqualTo(66);
        assertThat(response.records()).hasSize(1);
    }

    @Test
    void shouldQueryDbWithCorrectOffsetForPageTwo() {
        stubPage(List.of(app(3013L, "App 13")), 66L);

        PageResponse<?> response = aiAppApplicationService.listApps(user(), query(2, 12));

        verify(aiAppEntityMapper).findAccessibleAppsPage(
                eq(List.of(1001L)), isNull(), isNull(), isNull(), eq(12L), eq(12L));
        assertThat(response.pageNo()).isEqualTo(2);
    }

    @Test
    void shouldApplyKeywordStatusAndAppTypeFiltersInDbQuery() {
        stubPage(List.of(app(3001L, "CRM App")), 1L);

        AiAppQueryRequest request = query(1, 12);
        request.setKeyword("CRM");
        request.setStatus("DRAFT");
        request.setAppType("ADMIN_WEB");

        aiAppApplicationService.listApps(user(), request);

        verify(aiAppEntityMapper).countAccessibleApps(
                eq(List.of(1001L)), eq("CRM"), eq("DRAFT"), eq("ADMIN_WEB"));
        verify(aiAppEntityMapper).findAccessibleAppsPage(
                eq(List.of(1001L)), eq("CRM"), eq("DRAFT"), eq("ADMIN_WEB"), eq(0L), eq(12L));
    }

    @Test
    void shouldNormalizeInvalidPageSizeToDefaultTwelve() {
        stubPage(List.of(), 0L);

        AiAppQueryRequest request = query(1, 50);
        PageResponse<?> response = aiAppApplicationService.listApps(user(), request);

        verify(aiAppEntityMapper).findAccessibleAppsPage(
                anyList(), isNull(), isNull(), isNull(), anyLong(), eq(12L));
        assertThat(response.pageSize()).isEqualTo(12);
    }

    @Test
    void shouldNormalizePageNoLessThanOne() {
        stubPage(List.of(app(3001L, "App 1")), 1L);

        AiAppQueryRequest request = query(0, 12);
        PageResponse<?> response = aiAppApplicationService.listApps(user(), request);

        verify(aiAppEntityMapper).findAccessibleAppsPage(
                eq(List.of(1001L)), isNull(), isNull(), isNull(), eq(0L), eq(12L));
        assertThat(response.pageNo()).isEqualTo(1);
    }

    @Test
    void shouldClampPageNoWhenBeyondLastPage() {
        stubPage(List.of(app(3061L, "App 61")), 66L);

        PageResponse<?> response = aiAppApplicationService.listApps(user(), query(99, 12));

        verify(aiAppEntityMapper).findAccessibleAppsPage(
                eq(List.of(1001L)), isNull(), isNull(), isNull(), eq(60L), eq(12L));
        assertThat(response.pageNo()).isEqualTo(6);
    }

    @Test
    void shouldReturnEmptyPageWhenNoReadableWorkspaces() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of());

        PageResponse<?> response = aiAppApplicationService.listApps(user(), query(1, 12));

        assertThat(response.records()).isEmpty();
        assertThat(response.total()).isZero();
    }

    private void stubPage(List<AiAppEntity> records, long total) {
        given(aiAppEntityMapper.countAccessibleApps(anyList(), any(), any(), any())).willReturn(total);
        given(aiAppEntityMapper.findAccessibleAppsPage(anyList(), any(), any(), any(), anyLong(), anyLong()))
                .willReturn(records);
    }

    private static AiAppQueryRequest query(long pageNo, long pageSize) {
        AiAppQueryRequest request = new AiAppQueryRequest();
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }

    private static CurrentUser user() {
        return new CurrentUser(2001L, "reader", List.of("USER"));
    }

    private static AiAppEntity app(long id, String name) {
        AiAppEntity entity = AiAppEntity.builder()
                .id(id)
                .workspaceId(1001L)
                .name(name)
                .description("demo")
                .appType("WEB_APP")
                .status("DRAFT")
                .visibility("PRIVATE")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
        return entity;
    }
}
