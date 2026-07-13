package com.codeforge.ai.application.service.release;

import com.codeforge.ai.application.generation.AiCodegenPromptBuilder;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelCallPhase;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import com.codeforge.ai.domain.model.entity.ModelCallLogEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateEntity;
import com.codeforge.ai.domain.prompt.entity.PromptTemplateVersionEntity;
import com.codeforge.ai.domain.prompt.model.PromptFingerprintHasher;
import com.codeforge.ai.domain.prompt.model.PromptTemplateExecutionResolver;
import com.codeforge.ai.domain.prompt.model.ResolvedGenerationPrompt;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelCallLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.PromptTemplateVersionEntityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RuntimeFingerprintUsesFinalOutgoingMessagesTest {

    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private AiAppEntityMapper aiAppEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Mock private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Mock private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    @Mock private PromptResourceLoader promptResourceLoader;
    @InjectMocks private PromptFingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldMatchFingerprintFromFinalOutgoingMessagesNotRawTemplate() {
        stubPinnedTemplateTask();
        ModelCallLogEntity callLog = modelCall(148L, ModelCallPhase.INITIAL.generationSourceCode());
        given(modelCallLogEntityMapper.findById(148L)).willReturn(callLog);

        var result = service.verify(143L, 148L);

        assertThat(result.matchesPinnedVersion()).isTrue();
        assertThat(result.systemHashMatches()).isTrue();
        assertThat(result.userHashMatches()).isTrue();
        assertThat(result.combinedMatches()).isTrue();
    }

    private void stubPinnedTemplateTask() {
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(143L)
                .appId(3001L)
                .promptTemplateId(4001L)
                .promptTemplateVersionId(12L)
                .requestPayloadJson("""
                        {"requirement":"hello","promptTemplateId":4001,"promptTemplateVersionId":12,"templateVariables":{"requirement":"hello"}}
                        """)
                .build();
        given(generationTaskEntityMapper.selectOneById(143L)).willReturn(task);
        given(aiAppEntityMapper.selectOneById(3001L)).willReturn(
                AiAppEntity.builder().id(3001L).name("Demo").appType("WEB_APP").build());
        given(promptTemplateExecutionResolver.resolveOptional(eq(4001L), eq(12L), eq("hello"), any()))
                .willReturn(Optional.of(new ResolvedGenerationPrompt(
                        4001L, 12L, "T2", 1,
                        "CF_RUNTIME_TEMPLATE_SYSTEM_V1",
                        "CF_RUNTIME_TEMPLATE_USER_V1_hello",
                        null, null, null)));
        stubLatestVersionMismatch();
    }

    private void stubLatestVersionMismatch() {
        given(promptTemplateEntityMapper.selectOneById(4001L)).willReturn(PromptTemplateEntity.builder()
                .id(4001L).currentVersionNo(2).build());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(4001L, 2))
                .willReturn(PromptTemplateVersionEntity.builder()
                        .id(99L).templateId(4001L).versionNo(2)
                        .systemPrompt("CF_RUNTIME_TEMPLATE_SYSTEM_V2")
                        .userPrompt("CF_RUNTIME_TEMPLATE_USER_V2_hello")
                        .build());
        given(promptTemplateExecutionResolver.resolvePinned(eq(4001L), eq(99L), eq("hello"), any()))
                .willReturn(new ResolvedGenerationPrompt(
                        4001L, 99L, "T2", 2,
                        "CF_RUNTIME_TEMPLATE_SYSTEM_V2",
                        "CF_RUNTIME_TEMPLATE_USER_V2_hello",
                        null, null, null));
    }

    private ModelCallLogEntity modelCall(Long id, String generationSource) {
        GenerationContext context = new GenerationContext(
                "hello", "Demo", "WEB_APP", "HTML", 3001L, 8L, 143L,
                null, null, null, null, null,
                "CF_RUNTIME_TEMPLATE_SYSTEM_V1",
                "CF_RUNTIME_TEMPLATE_USER_V1_hello",
                4001L, 12L, "T2", 1);
        List<ModelMessage> messages = AiCodegenPromptBuilder.buildInitialMessages(
                context.systemPrompt(), context);
        var fingerprint = PromptFingerprintHasher.fromMessages(messages);
        return ModelCallLogEntity.builder()
                .id(id)
                .taskId(143L)
                .generationSource(generationSource)
                .promptTemplateVersionId(12L)
                .systemPromptSha256(fingerprint.systemSha256())
                .userPromptSha256(fingerprint.userSha256())
                .combinedPromptFingerprint(fingerprint.combined())
                .build();
    }
}

@ExtendWith(MockitoExtension.class)
class FingerprintVerifierUsesProductionCanonicalizationTest {

    @Test
    void shouldUsePromptFingerprintHasherFromMessagesContract() {
        List<ModelMessage> messages = List.of(
                ModelMessage.system("SYS"),
                ModelMessage.user("USER_A"),
                ModelMessage.user("USER_B"));
        var fingerprint = PromptFingerprintHasher.fromMessages(messages);
        var direct = PromptFingerprintHasher.hash("SYS", "USER_A\nUSER_B");
        assertThat(fingerprint.combined()).isEqualTo(direct.combined());
        assertThat(fingerprint.systemSha256()).isEqualTo(direct.systemSha256());
        assertThat(fingerprint.userSha256()).isEqualTo(direct.userSha256());
    }
}

@ExtendWith(MockitoExtension.class)
class InitialAttemptFingerprintMatchesInitialMessagesTest {

    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private AiAppEntityMapper aiAppEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Mock private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Mock private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    @Mock private PromptResourceLoader promptResourceLoader;
    @InjectMocks private PromptFingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldVerifyInitialAttemptWithInitialMessages() {
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(143L).appId(1L).promptTemplateId(1L).promptTemplateVersionId(12L)
                .requestPayloadJson("{\"requirement\":\"hello\"}")
                .build();
        given(generationTaskEntityMapper.selectOneById(143L)).willReturn(task);
        given(aiAppEntityMapper.selectOneById(1L)).willReturn(
                AiAppEntity.builder().id(1L).name("A").appType("WEB_APP").build());
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.of(new ResolvedGenerationPrompt(
                        1L, 12L, "T", 1, "SYS_V1", "USR_V1", null, null, null)));
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(
                PromptTemplateEntity.builder().id(1L).currentVersionNo(2).build());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 2))
                .willReturn(PromptTemplateVersionEntity.builder().id(22L).templateId(1L).versionNo(2).build());
        given(promptTemplateExecutionResolver.resolvePinned(eq(1L), eq(22L), any(), any()))
                .willReturn(new ResolvedGenerationPrompt(1L, 22L, "T", 2, "SYS_V2", "USR_V2", null, null, null));

        GenerationContext context = new GenerationContext(
                "hello", "A", "WEB_APP", "HTML", 1L, 1L, 143L,
                null, null, null, null, null, "SYS_V1", "USR_V1", 1L, 12L, "T", 1);
        var expected = PromptFingerprintHasher.fromMessages(
                AiCodegenPromptBuilder.buildInitialMessages("SYS_V1", context));
        given(modelCallLogEntityMapper.findById(148L)).willReturn(ModelCallLogEntity.builder()
                .id(148L).taskId(143L).generationSource(ModelCallPhase.INITIAL.generationSourceCode())
                .systemPromptSha256(expected.systemSha256())
                .userPromptSha256(expected.userSha256())
                .combinedPromptFingerprint(expected.combined())
                .build());

        var result = service.verify(143L, 148L);
        assertThat(result.attemptPhase()).isEqualTo("INITIAL");
        assertThat(result.matchesPinnedVersion()).isTrue();
    }
}

@ExtendWith(MockitoExtension.class)
class CompactAttemptFingerprintMatchesCompactMessagesTest {

    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private AiAppEntityMapper aiAppEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Mock private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Mock private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    @Mock private PromptResourceLoader promptResourceLoader;
    @InjectMocks private PromptFingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldVerifyCompactAttemptWithCompactMessages() {
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(143L).appId(1L).promptTemplateId(1L).promptTemplateVersionId(12L)
                .requestPayloadJson("{\"requirement\":\"hello\"}")
                .build();
        given(generationTaskEntityMapper.selectOneById(143L)).willReturn(task);
        given(aiAppEntityMapper.selectOneById(1L)).willReturn(
                AiAppEntity.builder().id(1L).name("A").appType("WEB_APP").build());
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.of(new ResolvedGenerationPrompt(
                        1L, 12L, "T", 1, "SYS_V1", "USR_V1", null, null, null)));
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(
                PromptTemplateEntity.builder().id(1L).currentVersionNo(1).build());

        GenerationContext context = new GenerationContext(
                "hello", "A", "WEB_APP", "HTML", 1L, 1L, 143L,
                null, null, null, null, null, "SYS_V1", "USR_V1", 1L, 12L, "T", 1);
        var expected = PromptFingerprintHasher.fromMessages(
                AiCodegenPromptBuilder.buildCompactMessages("SYS_V1", context));
        given(modelCallLogEntityMapper.findById(200L)).willReturn(ModelCallLogEntity.builder()
                .id(200L).taskId(143L).generationSource(ModelCallPhase.COMPACT_RETRY.generationSourceCode())
                .systemPromptSha256(expected.systemSha256())
                .userPromptSha256(expected.userSha256())
                .combinedPromptFingerprint(expected.combined())
                .promptTemplateVersionId(12L)
                .build());

        var result = service.verify(143L, 200L);
        assertThat(result.attemptPhase()).isEqualTo("COMPACT_RETRY");
        assertThat(result.matchesPinnedVersion()).isTrue();
    }
}

@ExtendWith(MockitoExtension.class)
class PinnedV1FingerprintDoesNotMatchLatestV2Test {

    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private AiAppEntityMapper aiAppEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Mock private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Mock private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    @Mock private PromptResourceLoader promptResourceLoader;
    @InjectMocks private PromptFingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void pinnedShouldMatchWhileLatestShouldNot() {
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(143L).appId(1L).promptTemplateId(1L).promptTemplateVersionId(12L)
                .requestPayloadJson("{\"requirement\":\"hello\"}")
                .build();
        given(generationTaskEntityMapper.selectOneById(143L)).willReturn(task);
        given(aiAppEntityMapper.selectOneById(1L)).willReturn(
                AiAppEntity.builder().id(1L).name("A").appType("WEB_APP").build());
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.of(new ResolvedGenerationPrompt(
                        1L, 12L, "T", 1, "SYS_V1", "USR_V1", null, null, null)));
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(
                PromptTemplateEntity.builder().id(1L).currentVersionNo(2).build());
        given(promptTemplateVersionEntityMapper.findByTemplateIdAndVersionNo(1L, 2))
                .willReturn(PromptTemplateVersionEntity.builder().id(22L).templateId(1L).versionNo(2).build());
        given(promptTemplateExecutionResolver.resolvePinned(eq(1L), eq(22L), any(), any()))
                .willReturn(new ResolvedGenerationPrompt(1L, 22L, "T", 2, "SYS_V2", "USR_V2", null, null, null));

        GenerationContext context = new GenerationContext(
                "hello", "A", "WEB_APP", "HTML", 1L, 1L, 143L,
                null, null, null, null, null, "SYS_V1", "USR_V1", 1L, 12L, "T", 1);
        var expected = PromptFingerprintHasher.fromMessages(
                AiCodegenPromptBuilder.buildInitialMessages("SYS_V1", context));
        given(modelCallLogEntityMapper.findById(148L)).willReturn(ModelCallLogEntity.builder()
                .id(148L).taskId(143L).generationSource(ModelCallPhase.INITIAL.generationSourceCode())
                .systemPromptSha256(expected.systemSha256())
                .userPromptSha256(expected.userSha256())
                .combinedPromptFingerprint(expected.combined())
                .build());

        var result = service.verify(143L, 148L);
        assertThat(result.matchesPinnedVersion()).isTrue();
        assertThat(result.matchesLatestVersion()).isFalse();
    }
}

@ExtendWith(MockitoExtension.class)
class ModelCallSelectionDoesNotCompareWrongAttemptTest {

    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private AiAppEntityMapper aiAppEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Mock private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Mock private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    @Mock private PromptResourceLoader promptResourceLoader;
    @InjectMocks private PromptFingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldFailWhenInitialExpectedComparedAgainstCompactCall() {
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(143L).appId(1L).promptTemplateId(1L).promptTemplateVersionId(12L)
                .requestPayloadJson("{\"requirement\":\"hello\"}")
                .build();
        given(generationTaskEntityMapper.selectOneById(143L)).willReturn(task);
        given(aiAppEntityMapper.selectOneById(1L)).willReturn(
                AiAppEntity.builder().id(1L).name("A").appType("WEB_APP").build());
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.of(new ResolvedGenerationPrompt(
                        1L, 12L, "T", 1, "SYS_V1", "USR_V1", null, null, null)));
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(
                PromptTemplateEntity.builder().id(1L).currentVersionNo(1).build());

        GenerationContext context = new GenerationContext(
                "hello", "A", "WEB_APP", "HTML", 1L, 1L, 143L,
                null, null, null, null, null, "SYS_V1", "USR_V1", 1L, 12L, "T", 1);
        var initial = PromptFingerprintHasher.fromMessages(
                AiCodegenPromptBuilder.buildInitialMessages("SYS_V1", context));
        var compact = PromptFingerprintHasher.fromMessages(
                AiCodegenPromptBuilder.buildCompactMessages("SYS_V1", context));
        given(modelCallLogEntityMapper.findById(201L)).willReturn(ModelCallLogEntity.builder()
                .id(201L).taskId(143L).generationSource(ModelCallPhase.COMPACT_RETRY.generationSourceCode())
                .systemPromptSha256(initial.systemSha256())
                .userPromptSha256(initial.userSha256())
                .combinedPromptFingerprint(initial.combined())
                .build());

        var result = service.verify(143L, 201L);
        assertThat(result.matchesPinnedVersion()).isFalse();
        assertThat(compact.combined()).isNotEqualTo(initial.combined());
    }
}

@ExtendWith(MockitoExtension.class)
class FingerprintVerificationNeverReturnsPromptContentTest {

    @Mock private GenerationTaskEntityMapper generationTaskEntityMapper;
    @Mock private ModelCallLogEntityMapper modelCallLogEntityMapper;
    @Mock private AiAppEntityMapper aiAppEntityMapper;
    @Mock private PromptTemplateExecutionResolver promptTemplateExecutionResolver;
    @Mock private PromptTemplateEntityMapper promptTemplateEntityMapper;
    @Mock private PromptTemplateVersionEntityMapper promptTemplateVersionEntityMapper;
    @Mock private PromptResourceLoader promptResourceLoader;
    @InjectMocks private PromptFingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void resultRecordShouldOnlyContainBooleanFlags() {
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(143L).appId(1L).promptTemplateId(1L).promptTemplateVersionId(12L)
                .requestPayloadJson("{\"requirement\":\"SECRET_PROMPT_MARKER\"}")
                .build();
        given(generationTaskEntityMapper.selectOneById(143L)).willReturn(task);
        given(aiAppEntityMapper.selectOneById(1L)).willReturn(
                AiAppEntity.builder().id(1L).name("A").appType("WEB_APP").build());
        given(promptTemplateExecutionResolver.resolveOptional(any(), any(), any(), any()))
                .willReturn(Optional.of(new ResolvedGenerationPrompt(
                        1L, 12L, "T", 1, "SECRET_PROMPT_MARKER", "SECRET_PROMPT_MARKER", null, null, null)));
        given(promptTemplateEntityMapper.selectOneById(1L)).willReturn(
                PromptTemplateEntity.builder().id(1L).currentVersionNo(1).build());
        given(modelCallLogEntityMapper.findById(1L)).willReturn(ModelCallLogEntity.builder()
                .id(1L).taskId(143L).generationSource(ModelCallPhase.INITIAL.generationSourceCode())
                .systemPromptSha256("abc").userPromptSha256("def").combinedPromptFingerprint("ghi")
                .build());

        var result = service.verify(143L, 1L);
        assertThat(result.toString()).doesNotContain("SECRET_PROMPT_MARKER");
    }
}
