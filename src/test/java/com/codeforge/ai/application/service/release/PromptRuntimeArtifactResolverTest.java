package com.codeforge.ai.application.service.release;

import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.task.entity.GenerationTaskEventEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskEventType;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEventEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ArtifactResolverUsesVersionCreatedEventTest {

    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @InjectMocks private PromptRuntimeArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldResolveFromVersionCreatedEvent() {
        given(generationTaskEventEntityMapper.findByTaskId(143L)).willReturn(List.of(event(
                GenerationTaskEventType.VERSION_CREATED.name(), "{\"versionId\":95}")));
        given(appVersionEntityMapper.findBySourceTaskId(143L)).willReturn(List.of());
        given(appVersionEntityMapper.findById(95L)).willReturn(version(95L, 3001L));

        TaskArtifactBindingResult result = resolver.resolve(143L, 3001L);

        assertThat(result.resolved()).isTrue();
        assertThat(result.appVersionId()).isEqualTo(95L);
        assertThat(result.bindingSource()).isEqualTo(TaskArtifactBindingResult.SOURCE_VERSION_CREATED_EVENT);
    }

    private static GenerationTaskEventEntity event(String type, String payload) {
        return GenerationTaskEventEntity.builder()
                .taskId(143L)
                .eventType(type)
                .eventPayloadJson(payload)
                .build();
    }

    private static AppVersionEntity version(Long id, Long appId) {
        return AppVersionEntity.builder().id(id).appId(appId).versionNo(1).build();
    }
}

@ExtendWith(MockitoExtension.class)
class ArtifactResolverUsesTaskSuccessEventTest {

    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @InjectMocks private PromptRuntimeArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldResolveFromTaskSuccessEventWhenVersionCreatedMissing() {
        given(generationTaskEventEntityMapper.findByTaskId(143L)).willReturn(List.of(event(
                GenerationTaskEventType.TASK_SUCCESS.name(), "{\"versionId\":95}")));
        given(appVersionEntityMapper.findBySourceTaskId(143L)).willReturn(List.of());
        given(appVersionEntityMapper.findById(95L)).willReturn(version(95L, 3001L));

        TaskArtifactBindingResult result = resolver.resolve(143L, 3001L);

        assertThat(result.resolved()).isTrue();
        assertThat(result.bindingSource()).isEqualTo(TaskArtifactBindingResult.SOURCE_TASK_SUCCESS_EVENT);
    }

    private static GenerationTaskEventEntity event(String type, String payload) {
        return GenerationTaskEventEntity.builder()
                .taskId(143L)
                .eventType(type)
                .eventPayloadJson(payload)
                .build();
    }

    private static AppVersionEntity version(Long id, Long appId) {
        return AppVersionEntity.builder().id(id).appId(appId).versionNo(1).build();
    }
}

@ExtendWith(MockitoExtension.class)
class ArtifactResolverRejectsConflictingEventVersionIdsTest {

    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @InjectMocks private PromptRuntimeArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldRejectConflictingVersionIds() {
        given(generationTaskEventEntityMapper.findByTaskId(143L)).willReturn(List.of(
                event(GenerationTaskEventType.VERSION_CREATED.name(), "{\"versionId\":95}"),
                event(GenerationTaskEventType.TASK_SUCCESS.name(), "{\"versionId\":96}")));
        given(appVersionEntityMapper.findBySourceTaskId(143L)).willReturn(List.of());

        TaskArtifactBindingResult result = resolver.resolve(143L, 3001L);

        assertThat(result.resolved()).isFalse();
        assertThat(result.errorCode()).isEqualTo(TaskArtifactBindingResult.ERROR_CONFLICTING);
    }

    private static GenerationTaskEventEntity event(String type, String payload) {
        return GenerationTaskEventEntity.builder()
                .taskId(143L)
                .eventType(type)
                .eventPayloadJson(payload)
                .build();
    }
}

@ExtendWith(MockitoExtension.class)
class ArtifactResolverRejectsVersionFromAnotherAppTest {

    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @InjectMocks private PromptRuntimeArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldRejectVersionBelongingToAnotherApp() {
        given(generationTaskEventEntityMapper.findByTaskId(143L)).willReturn(List.of(event(
                GenerationTaskEventType.VERSION_CREATED.name(), "{\"versionId\":95}")));
        given(appVersionEntityMapper.findBySourceTaskId(143L)).willReturn(List.of());
        given(appVersionEntityMapper.findById(95L)).willReturn(version(95L, 9999L));

        TaskArtifactBindingResult result = resolver.resolve(143L, 3001L);

        assertThat(result.resolved()).isFalse();
        assertThat(result.errorCode()).isEqualTo(TaskArtifactBindingResult.ERROR_VERSION_APP_MISMATCH);
    }

    private static GenerationTaskEventEntity event(String type, String payload) {
        return GenerationTaskEventEntity.builder()
                .taskId(143L)
                .eventType(type)
                .eventPayloadJson(payload)
                .build();
    }

    private static AppVersionEntity version(Long id, Long appId) {
        return AppVersionEntity.builder().id(id).appId(appId).versionNo(1).build();
    }
}

@ExtendWith(MockitoExtension.class)
class ArtifactResolverFailsWhenExactVersionCannotBeResolvedTest {

    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @InjectMocks private PromptRuntimeArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldFailWhenNoTaskBoundVersionExists() {
        given(generationTaskEventEntityMapper.findByTaskId(143L)).willReturn(List.of());
        given(appVersionEntityMapper.findBySourceTaskId(143L)).willReturn(List.of());

        TaskArtifactBindingResult result = resolver.resolve(143L, 3001L);

        assertThat(result.resolved()).isFalse();
        assertThat(result.errorCode()).isEqualTo(TaskArtifactBindingResult.ERROR_UNRESOLVED);
        assertThat(result.appVersionId()).isNull();
    }
}

@ExtendWith(MockitoExtension.class)
class ArtifactResolverNeverFallsBackToCurrentAppVersionTest {

    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @InjectMocks private PromptRuntimeArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldNotUseCurrentAppVersionWhenTaskEvidenceMissing() {
        given(generationTaskEventEntityMapper.findByTaskId(143L)).willReturn(List.of());
        given(appVersionEntityMapper.findBySourceTaskId(143L)).willReturn(List.of());

        TaskArtifactBindingResult result = resolver.resolve(143L, 3001L);

        assertThat(result.resolved()).isFalse();
        assertThat(result.appVersionId()).isNull();
    }
}

@ExtendWith(MockitoExtension.class)
class NewerAppVersionDoesNotChangeOldTaskArtifactResolutionTest {

    @Mock private GenerationTaskEventEntityMapper generationTaskEventEntityMapper;
    @Mock private AppVersionEntityMapper appVersionEntityMapper;
    @InjectMocks private PromptRuntimeArtifactResolver resolver;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(resolver, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldKeepTaskBoundVersionEvenIfNewerVersionExistsForApp() {
        given(generationTaskEventEntityMapper.findByTaskId(135L)).willReturn(List.of(event(
                GenerationTaskEventType.VERSION_CREATED.name(), "{\"versionId\":95}")));
        given(appVersionEntityMapper.findBySourceTaskId(135L)).willReturn(List.of(version(95L, 3001L)));
        given(appVersionEntityMapper.findById(95L)).willReturn(version(95L, 3001L));

        TaskArtifactBindingResult result = resolver.resolve(135L, 3001L);

        assertThat(result.resolved()).isTrue();
        assertThat(result.appVersionId()).isEqualTo(95L);
    }

    private static GenerationTaskEventEntity event(String type, String payload) {
        return GenerationTaskEventEntity.builder()
                .taskId(135L)
                .eventType(type)
                .eventPayloadJson(payload)
                .build();
    }

    private static AppVersionEntity version(Long id, Long appId) {
        return AppVersionEntity.builder().id(id).appId(appId).versionNo(1).build();
    }
}
