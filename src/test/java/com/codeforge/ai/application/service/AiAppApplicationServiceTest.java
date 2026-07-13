package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.app.AiAppCreateRequest;
import com.codeforge.ai.application.dto.app.AiAppDetailResponse;
import com.codeforge.ai.application.dto.app.AiAppQueryRequest;
import com.codeforge.ai.application.dto.app.AiAppUpdateRequest;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiAppApplicationServiceTest {

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
    }

    @Test
    void shouldCreateDraftPrivateApp() {
        doAnswer(invocation -> {
            AiAppEntity entity = invocation.getArgument(0);
            entity.setCreatedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
            entity.setUpdatedAt(entity.getCreatedAt());
            return 1;
        }).when(aiAppEntityMapper).insertApp(any(AiAppEntity.class));

        AiAppCreateRequest request = new AiAppCreateRequest();
        request.setWorkspaceId(1001L);
        request.setName("App A");
        request.setDescription("demo");
        request.setAppType("WEB_APP");

        AiAppDetailResponse response = aiAppApplicationService.createApp(
                new CurrentUser(2001L, "editor", List.of("USER")),
                request
        );

        ArgumentCaptor<AiAppEntity> captor = ArgumentCaptor.forClass(AiAppEntity.class);
        verify(aiAppEntityMapper).insertApp(captor.capture());
        AiAppEntity entity = captor.getValue();
        assertThat(entity.getWorkspaceId()).isEqualTo(1001L);
        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getStatus()).isEqualTo("DRAFT");
        assertThat(entity.getVisibility()).isEqualTo("PRIVATE");
        assertThat(entity.getCreatedBy()).isEqualTo(2001L);
        assertThat(response.id()).isEqualTo(entity.getId());
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    void shouldListAppsForReadableWorkspace() {
        given(workspaceAccessService.listReadableWorkspaceIds(any())).willReturn(List.of(1001L));
        AiAppEntity entity = AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .description("demo")
                .appType("WEB_APP")
                .status("DRAFT")
                .visibility("PRIVATE")
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 6, 22, 16, 0));
        given(aiAppEntityMapper.countAccessibleApps(eq(List.of(1001L)), eq("App"), eq("DRAFT"), eq("WEB_APP")))
                .willReturn(1L);
        given(aiAppEntityMapper.findAccessibleAppsPage(
                eq(List.of(1001L)), eq("App"), eq("DRAFT"), eq("WEB_APP"), eq(0L), eq(12L)))
                .willReturn(List.of(entity));

        AiAppQueryRequest request = new AiAppQueryRequest();
        request.setKeyword("App");
        request.setStatus("DRAFT");
        request.setAppType("WEB_APP");
        request.setPageNo(1);
        request.setPageSize(10);

        PageResponse<?> response = aiAppApplicationService.listApps(
                new CurrentUser(2001L, "reader", List.of("USER")), request);

        assertThat(response.records()).hasSize(1);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(12);
    }

    @Test
    void shouldRejectInvalidVisibilityOnUpdate() {
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(AiAppEntity.builder()
                .id(3001L)
                .workspaceId(1001L)
                .name("App A")
                .description("demo")
                .appType("WEB_APP")
                .status("DRAFT")
                .visibility("PRIVATE")
                .build());

        AiAppUpdateRequest request = new AiAppUpdateRequest();
        request.setVisibility("INVALID");

        assertThatThrownBy(() -> aiAppApplicationService.updateApp(
                new CurrentUser(2001L, "editor", List.of("USER")), 3001L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("visibility");
    }
}
