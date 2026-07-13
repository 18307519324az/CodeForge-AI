package com.codeforge.ai.shared.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedArtifactPathSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void RepairRejectsParentTraversalPathTest() {
        assertRejected("../outside.html");
        assertRejected("../../outside.html");
        assertRejected("assets/../../../outside.html");
    }

    @Test
    void RepairRejectsAbsoluteUnixPathTest() {
        assertRejected("/tmp/outside.html");
    }

    @Test
    void RepairRejectsWindowsDrivePathTest() {
        assertRejected("C:\\temp\\outside.html");
        assertRejected("C:/temp/outside.html");
    }

    @Test
    void RepairRejectsUncPathTest() {
        assertRejected("\\\\server\\share\\outside.html");
        assertRejected("//server/share/outside.html");
    }

    @Test
    void RepairRejectsNullBytePathTest() {
        assertRejected("index.html\0/evil");
    }

    @Test
    void RepairRejectsCurrentDirectorySegmentTest() {
        assertRejected("./");
        assertRejected("a/./b");
    }

    @Test
    void RepairRejectsEmptyPathSegmentTest() {
        assertRejected("a//b");
    }

    @Test
    void RepairRejectsColonSegmentTest() {
        assertRejected("a:b");
    }

    @Test
    void RepairTargetMustRemainInsideVersionRootTest() {
        Path versionRoot = tempDir.resolve("apps/1/versions/9").toAbsolutePath().normalize();
        Path target = GeneratedArtifactPathSupport.resolveTargetPath(versionRoot, "assets/images/logo.png");
        assertThatThrownBy(() -> GeneratedArtifactPathSupport.resolveTargetPath(versionRoot, "../escape.html"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
        org.assertj.core.api.Assertions.assertThat(target.startsWith(versionRoot)).isTrue();
    }

    private void assertRejected(String filePath) {
        assertThatThrownBy(() -> GeneratedArtifactPathSupport.normalizeRelativeFilePath(filePath, filePath))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ARTIFACT_PATH_OUTSIDE_VERSION_ROOT);
    }
}
