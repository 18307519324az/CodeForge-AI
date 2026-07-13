package com.codeforge.ai.domain.generation;

import com.codeforge.ai.domain.generation.GeneratedProject.GeneratedProjectFile;
import com.codeforge.ai.domain.generation.business.BusinessPreset;
import com.codeforge.ai.domain.generation.business.BusinessPresetRegistry;
import com.codeforge.ai.domain.generation.business.BusinessScore;
import com.codeforge.ai.domain.generation.business.BusinessScoreEvaluator;
import com.codeforge.ai.shared.util.MojibakeDetector;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class GeneratedProjectQualityValidator {

    private static final int MIN_MAIN_FILE_LENGTH = 3000;
    private static final int MIN_STANDALONE_CSS_LENGTH = 800;
    private static final List<String> FORBIDDEN_STRINGS = List.of(
            "项目文件清单",
            "请下载后运行",
            "下面是生成结果",
            "以下是生成结果"
    );

    public ValidationResult validate(GeneratedProject project) {
        return validate(project, null);
    }

    public ValidationResult validate(GeneratedProject project, GenerationContext context) {
        if (project == null || project.files() == null || project.files().isEmpty()) {
            return ValidationResult.failed("生成结果没有包含文件");
        }

        GeneratedProjectFile mainFile = findMainFile(project.files());
        if (mainFile == null) {
            return ValidationResult.failed("生成结果没有包含任何 HTML 或 Vue 主文件");
        }

        for (GeneratedProjectFile file : project.files()) {
            ValidationResult fileResult = validateFileContent(file);
            if (!fileResult.passed()) {
                return fileResult;
            }
        }

        if (mainFile.content().length() < MIN_MAIN_FILE_LENGTH) {
            return ValidationResult.failed("主文件内容过短，未达到最低质量要求");
        }

        if (isVueFile(mainFile.filePath())) {
            ValidationResult vueResult = validateVueMainFile(mainFile.content());
            if (!vueResult.passed()) {
                return vueResult;
            }
        } else {
            ValidationResult htmlResult = validateHtmlMainFile(mainFile.content());
            if (!htmlResult.passed()) {
                return htmlResult;
            }
        }

        if (context != null && isPromptEcho(mainFile.content(), context.requirement())) {
            return ValidationResult.failed("生成页面标题与用户原始需求完全相同");
        }

        if (context != null) {
            ValidationResult businessMatch = validateBusinessMatch(mainFile.content(), context.requirement());
            if (!businessMatch.passed()) {
                return businessMatch;
            }
        }

        return ValidationResult.ok();
    }

    private GeneratedProjectFile findMainFile(List<GeneratedProjectFile> files) {
        return files.stream()
                .filter(file -> file.filePath() != null)
                .filter(file -> isHtmlFile(file.filePath()) || isVueFile(file.filePath()))
                .findFirst()
                .orElse(null);
    }

    private ValidationResult validateFileContent(GeneratedProjectFile file) {
        if (file.content() == null || file.content().isBlank()) {
            return ValidationResult.failed("文件内容不能为空: " + file.filePath());
        }
        if (file.content().indexOf('\u0000') >= 0) {
            return ValidationResult.failed("文件内容包含空字节: " + file.filePath());
        }
        if (file.content().indexOf('\uFFFD') >= 0) {
            return ValidationResult.failed("文件内容包含乱码或 FFFD 替换字符: " + file.filePath());
        }
        if (MojibakeDetector.containsMojibake(file.content())) {
            return ValidationResult.failed("文件内容包含乱码: " + file.filePath());
        }

        String path = lower(file.filePath());
        if (path.endsWith(".json")) {
            String trimmed = file.content().trim();
            if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                return ValidationResult.failed("JSON 文件格式非法: " + file.filePath());
            }
        }
        if (path.endsWith(".css") && file.content().length() < MIN_STANDALONE_CSS_LENGTH) {
            return ValidationResult.failed("独立 CSS 文件过短: " + file.filePath());
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateVueMainFile(String content) {
        if (!content.contains("<template")) {
            return ValidationResult.failed("Vue 主文件缺少 <template> 结构");
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateHtmlMainFile(String html) {
        String lower = lower(html);
        if (!lower.contains("<!doctype html>") || !lower.contains("<html")) {
            return ValidationResult.failed("HTML 结构不完整，缺少 DOCTYPE 或 html 标签");
        }
        if (!lower.contains("charset=\"utf-8\"") && !lower.contains("charset='utf-8'")) {
            return ValidationResult.failed("HTML 缺少 UTF-8 charset 声明");
        }
        if (!containsTableOrGrid(lower)) {
            return ValidationResult.failed("HTML 缺少表格或网格布局");
        }
        if (!containsFormControls(lower)) {
            return ValidationResult.failed("HTML 缺少表单或交互控件");
        }
        if (!containsEnoughBusinessText(html)) {
            return ValidationResult.failed("HTML 缺少足够的中文业务内容");
        }
        for (String forbidden : FORBIDDEN_STRINGS) {
            if (html.contains(forbidden)) {
                return ValidationResult.failed("HTML 包含禁用提示语: " + forbidden);
            }
        }
        return ValidationResult.ok();
    }

    private boolean containsTableOrGrid(String html) {
        return html.contains("<table") || html.contains("display:grid") || html.contains("display: grid");
    }

    private boolean containsFormControls(String html) {
        return html.contains("<form")
                || html.contains("<input")
                || html.contains("<select")
                || html.contains("<textarea")
                || html.contains("<button");
    }

    private boolean containsEnoughBusinessText(String html) {
        long ideographCount = html.codePoints().filter(Character::isIdeographic).count();
        return ideographCount >= 20;
    }

    private boolean isPromptEcho(String html, String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return false;
        }
        String normalizedRequirement = normalize(requirement);
        if (normalizedRequirement.isEmpty()) {
            return false;
        }

        return normalize(extractTagText(html, "title")).equals(normalizedRequirement)
                || normalize(extractTagText(html, "h1")).equals(normalizedRequirement);
    }

    private String extractTagText(String html, String tagName) {
        String lower = lower(html);
        String startTag = "<" + tagName;
        int start = lower.indexOf(startTag);
        if (start < 0) {
            return "";
        }
        int startClose = lower.indexOf(">", start);
        int end = lower.indexOf("</" + tagName + ">", startClose);
        if (startClose < 0 || end < 0 || end <= startClose) {
            return "";
        }
        return html.substring(startClose + 1, end);
    }

    private ValidationResult validateBusinessMatch(String html, String requirement) {
        if (useBusinessScore()) {
            BusinessPreset preset = BusinessPresetRegistry.resolve(null, requirement);
            BusinessScore score = BusinessScoreEvaluator.evaluate(html, preset);
            if (score.passed(BusinessScoreEvaluator.PASSING_SCORE)) {
                return ValidationResult.ok();
            }
            return ValidationResult.failed("Business Score too low: " + score.score()
                    + ", businessType=" + score.businessType()
                    + ", moduleCount=" + score.moduleCount()
                    + ", entityCount=" + score.entityCount()
                    + ", crudCount=" + score.crudCount());
        }
        BusinessIntent intent = new RequirementIntentAnalyzer().classify(requirement);
        return switch (intent) {
            case TODO_LIST -> requireKeywords(html,
                    List.of("任务", "优先级", "截止", "完成", "新增任务"),
                    "待办类页面缺少关键业务字段");
            case ECOMMERCE_ADMIN -> requireKeywords(html,
                    List.of("商品", "订单", "用户", "库存"),
                    "商城后台缺少商品/订单/用户模块");
            case CUSTOMER_CRM -> requireKeywords(html,
                    List.of("客户", "联系方式", "等级", "详情"),
                    "客户管理页面缺少客户业务字段");
            case TICKET_SYSTEM -> requireKeywords(html,
                    List.of("工单", "状态", "优先级", "提交"),
                    "工单系统缺少工单关键字段");
            default -> ValidationResult.ok();
        };
    }

    private ValidationResult requireKeywords(String html, List<String> keywords, String message) {
        long count = keywords.stream().filter(html::contains).count();
        return count >= 3 ? ValidationResult.ok() : ValidationResult.failed(message);
    }

    private boolean isHtmlFile(String path) {
        return lower(path).endsWith(".html");
    }

    private boolean useBusinessScore() {
        return true;
    }

    private boolean isVueFile(String path) {
        return lower(path).endsWith(".vue");
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "")
                .replace("，", "")
                .replace(",", "")
                .replace("。", "")
                .replace(".", "")
                .trim();
    }

    public record ValidationResult(boolean passed, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failed(String message) {
            return new ValidationResult(false, message);
        }
    }
}
