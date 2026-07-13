package com.codeforge.ai.brand;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeForgeBrandAssetGateTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path FRONTEND_PUBLIC_BRAND = PROJECT_ROOT.resolve("frontend/public/brand/codeforge-mascot.png");
    private static final Path BACKEND_BRAND = PROJECT_ROOT.resolve("src/main/resources/brand/codeforge-mascot.png");
    private static final List<String> LEGACY_TRACKED_FILES = List.of(
            "frontend/public/favicon.ico",
            "frontend/src/assets/aiAvatar.png",
            "frontend/src/assets/logo.png");
    private static final Pattern LEGACY_REFERENCE = Pattern.compile(
            "(?i)aiavatar|logo\\.png|favicon\\.ico|yupi|鱼皮|yu-pi|yu_pi");
    private static final Pattern REMOTE_LEGACY_IMAGE = Pattern.compile(
            "(?i)https?://[^\\s\"']*(?:aiavatar|logo|yupi|鱼皮)[^\\s\"']*\\.(?:png|jpg|jpeg|webp|gif|svg|ico)");
    private static final Pattern BASE64_IMAGE = Pattern.compile("data:image/(?:png|jpeg|webp|svg\\+xml);base64,");

    @Test
    void CodeForgeMascotAssetExistsTest() throws IOException {
        assertThat(FRONTEND_PUBLIC_BRAND).exists().isRegularFile();
        assertThat(Files.size(FRONTEND_PUBLIC_BRAND)).isGreaterThan(1024L);
        assertThat(BACKEND_BRAND).exists().isRegularFile();
    }

    @Test
    void LegacyMascotFileIsRemovedTest() {
        for (String relativePath : LEGACY_TRACKED_FILES) {
            assertThat(PROJECT_ROOT.resolve(relativePath))
                    .as("legacy asset should be deleted: " + relativePath)
                    .doesNotExist();
        }
    }

    @Test
    void LegacyMascotAssetIsNotReferencedTest() throws IOException {
        List<Path> sourceRoots = List.of(
                PROJECT_ROOT.resolve("frontend/src"),
                PROJECT_ROOT.resolve("frontend/index.html"),
                PROJECT_ROOT.resolve("src/main/java"),
                PROJECT_ROOT.resolve("src/main/resources"));
        for (Path root : sourceRoots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> !path.toString().contains("node_modules"))
                        .filter(path -> !path.toString().contains("target"))
                        .filter(path -> !path.toString().contains("dist"))
                        .filter(CodeForgeBrandAssetGateTest::isScannableTextFile)
                        .filter(path -> !path.getFileName().toString().endsWith(".test.ts"))
                        .filter(path -> !path.getFileName().toString().equals("BrandAssetReferenceRewriter.java"))
                        .forEach(path -> assertNoLegacyReference(path));
            }
        }
    }

    @Test
    void FaviconUsesCodeForgeBrandAssetTest() throws IOException {
        String indexHtml = Files.readString(PROJECT_ROOT.resolve("frontend/index.html"), StandardCharsets.UTF_8);
        assertThat(indexHtml).contains("/brand/codeforge-mascot.png");
        assertThat(indexHtml.toLowerCase()).doesNotContain("favicon.ico");
    }

    @Test
    void FloatingAssistantUsesCodeForgeMascotTest() throws IOException {
        String chatPage = Files.readString(
                PROJECT_ROOT.resolve("frontend/src/pages/app/AppChatPage.vue"), StandardCharsets.UTF_8);
        assertThat(chatPage).contains("CODEFORGE_MASCOT_URL");
        assertThat(chatPage).doesNotContain("aiAvatar");
    }

    @Test
    void NoLegacyMascotRemoteUrlTest() throws IOException {
        scanTextSources(this::assertNoRemoteLegacyImage);
    }

    @Test
    void NoLegacyMascotBase64CopyTest() throws IOException {
        scanTextSources(path -> {
            String content = readUtf8(path);
            assertThat(BASE64_IMAGE.matcher(content).find())
                    .as("unexpected base64 image in " + path)
                    .isFalse();
        });
    }

    private void scanTextSources(TextConsumer consumer) throws IOException {
        List<Path> roots = List.of(
                PROJECT_ROOT.resolve("frontend/src"),
                PROJECT_ROOT.resolve("frontend/index.html"),
                PROJECT_ROOT.resolve("src/main/java"));
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            if (Files.isRegularFile(root)) {
                consumer.accept(root);
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> hasTextExtension(path))
                        .forEach(path -> {
                            try {
                                consumer.accept(path);
                            } catch (IOException exception) {
                                throw new AssertionError("failed reading " + path, exception);
                            }
                        });
            }
        }
    }

    private static boolean hasTextExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".java")
                || name.endsWith(".vue")
                || name.endsWith(".ts")
                || name.endsWith(".tsx")
                || name.endsWith(".html")
                || name.endsWith(".css")
                || name.endsWith(".json");
    }

    private static boolean isScannableTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".java")
                || name.endsWith(".vue")
                || name.endsWith(".ts")
                || name.endsWith(".tsx")
                || name.endsWith(".html")
                || name.endsWith(".css")
                || name.endsWith(".json")
                || name.endsWith(".md")
                || name.endsWith(".sql")
                || name.endsWith(".txt")
                || name.endsWith(".properties")
                || name.endsWith(".yml")
                || name.endsWith(".yaml");
    }

    private void assertNoLegacyReference(Path path) {
        String content = readUtf8(path);
        assertThat(LEGACY_REFERENCE.matcher(content).find())
                .as("legacy mascot reference in " + path)
                .isFalse();
    }

    private void assertNoRemoteLegacyImage(Path path) {
        String content = readUtf8(path);
        assertThat(REMOTE_LEGACY_IMAGE.matcher(content).find())
                .as("remote legacy image in " + path)
                .isFalse();
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("failed reading " + path, exception);
        }
    }

    @FunctionalInterface
    private interface TextConsumer {
        void accept(Path path) throws IOException;
    }
}
