package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.ChartArchiveInspectionPort.InspectedArchive;
import java.util.*;
import java.util.regex.Pattern;

/** Chart 원문을 주소가 있는 작은 조각으로 나누고 필요한 자료를 추가 조회한다. */
final class HelmValuesReferences {
    private static final int PAGE_CHARS = 6000;
    private static final int CONTEXT_CHARS = 18000;
    private final List<Page> pages = new ArrayList<>();
    private final String templateOnlyPaths;
    private final String compactRoot;

    /** 원본 Values와 Schema를 우선 등록하며 자료의 파일명·줄 위치를 보존한다. */
    HelmValuesReferences(InspectedArchive archive) {
        var defaults = HelmValuesDefaults.combined(archive);
        templateOnlyPaths = archive.templateValuePaths().stream().sorted()
                .filter(path -> defaults.at(path).isMissingNode())
                .collect(java.util.stream.Collectors.joining("\n"));
        String protectedRoot = ValuesReferenceText.mask(archive.defaultValuesYaml());
        compactRoot = ChartValuesEligibility.parse(protectedRoot).toString();
        add("values.yaml", protectedRoot);
        if (archive.valuesSchemaJson() != null) add("values.schema.json", ValuesReferenceText.mask(archive.valuesSchemaJson()));
        // Chart dependency condition과 alias도 모델이 실제 archive에서 확인한다.
        if (archive.chartYaml() != null && !archive.chartYaml().isBlank()) add("Chart.yaml", ValuesReferenceText.mask(archive.chartYaml()));
        if (!archive.templateValuePaths().isEmpty())
            add("template-value-paths", archive.templateValuePaths().stream().sorted().collect(java.util.stream.Collectors.joining("\n")));
        archive.referenceFiles().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (entry.getKey().endsWith("values.yaml") && !entry.getKey().equals("values.yaml"))
                add(entry.getKey(), ValuesReferenceText.mask(entry.getValue()));
        });
    }

    /** 원문을 줄 경계에서 나누며 한 줄이 너무 크면 위치가 명확한 연속 조각으로 보관한다. */
    private void add(String source, String text) {
        var paths = ValuesReferencePaths.index(source, text);
        int start = 0, line = 1;
        while (start < text.length()) {
            int end = Math.min(start + PAGE_CHARS, text.length());
            if (end < text.length()) {
                int split = text.lastIndexOf('\n', end);
                if (split > start) end = split + 1;
            }
            String body = text.substring(start, end);
            String keys = Pattern.compile("(?m)^\\s*([A-Za-z][\\w.-]*):").matcher(body).results()
                    .map(match -> match.group(1)).distinct().limit(24).reduce((a, b) -> a + "," + b).orElse("");
            int nextLine = line + (int) body.chars().filter(ch -> ch == '\n').count();
            int firstLine = line;
            String exactPaths = paths.entrySet().stream().filter(entry -> entry.getKey() >= firstLine && entry.getKey() <= nextLine)
                    .flatMap(entry -> entry.getValue().stream()).collect(java.util.stream.Collectors.joining("\n"));
            pages.add(new Page("ref" + pages.size(), source, line, scopeAt(text, start), keys, body, exactPaths));
            line = nextLine;
            start = end;
        }
    }

    /** 본문 대신 파일·위치·key 목록을 노출해 모델이 읽지 않은 자료를 요청할 수 있게 한다. */
    String index() {
        return pages.stream().map(page -> page.id + " " + page.source + ":" + page.line + " scope=" + page.scope + " keys=" + page.keys)
                .reduce((a, b) -> a + "\n" + b).orElse("")
                + "\nEXACT TEMPLATE PATHS OMITTED FROM DEFAULT VALUES (optional; use only when requested):\n" + templateOnlyPaths;
    }

    /** 사용자 표현에 등장하는 실제 key를 기준으로 우선 자료를 고르며 누락 페이지는 색인에 남긴다. */
    List<String> initial(String instruction) {
        String lower = instruction.toLowerCase(Locale.ROOT);
        return pages.stream().filter(page -> page.source.equals("values.yaml"))
                .sorted(Comparator.comparingInt((Page page) -> score(page, lower)).reversed())
                .limit(2).map(Page::id).toList();
    }

    /** 큰 주석 때문에 일부 설정만 전달하지 않도록 전체 루트 구조를 먼저 제공한다. */
    String initialEvidence(String instruction) {
        if (pages.stream().filter(page -> page.source.equals("values.yaml")).count() > 2
                && compactRoot.length() <= CONTEXT_CHARS)
            return "COMPLETE ROOT DEFAULT VALUES (JSON preserves all YAML keys, types and nesting; no fields omitted):\n"
                    + compactRoot + "\nOriginal comments and detailed descriptions remain available via referenceIds.\n";
        return read(initial(instruction));
    }

    /** 제품 이름을 하드코딩하지 않고 실제 key와 요청의 일치도를 계산한다. */
    private int score(Page page, String instruction) {
        int score = page.source.equals("values.yaml") ? 12 : 0;
        String body = page.body.toLowerCase(Locale.ROOT);
        for (String word : instruction.split("[^a-z0-9]+"))
            if (word.length() > 2 && body.contains(word)) score += 4;
        return score;
    }

    /** 요청한 원문 조각만 전달하고 잘못된 주소나 과대 요청은 조용히 자르지 않는다. */
    String read(List<String> ids) {
        if (ids.isEmpty() || ids.size() > 3) throw new IllegalArgumentException("한 번에 1~3개의 Chart 자료를 요청해야 합니다.");
        StringBuilder out = new StringBuilder();
        for (String id : new LinkedHashSet<>(ids)) {
            Page page = pages.stream().filter(item -> item.id.equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("알 수 없는 Chart 자료 주소입니다."));
            out.append("\n--- ").append(page.id).append(' ').append(page.source).append(':').append(page.line)
                    .append(" continuation-scope=").append(page.scope).append(" ---\n").append(page.body);
            out.append("\nEXACT VALUES PATHS FOR THIS PAGE (root-relative, not relative to continuation-scope):\n")
                    .append(page.exactPaths).append('\n');
        }
        if (out.length() > CONTEXT_CHARS + 8192) throw new IllegalArgumentException("Chart 자료 요청 범위를 초과했습니다.");
        return out.toString();
    }

    /** 원문 중간부터 읽을 때 들여쓰기의 상위 mapping을 잃지 않도록 문맥을 표시한다. */
    private String scopeAt(String text, int offset) {
        record Level(int indent, String key) { }
        List<Level> levels = new ArrayList<>();
        var matcher = Pattern.compile("(?m)^( *)([A-Za-z][\\w.-]*):([^\\n]*)").matcher(text.substring(0, offset));
        while (matcher.find()) {
            int indent = matcher.group(1).length();
            levels.removeIf(level -> level.indent() >= indent);
            if (matcher.group(3).strip().isEmpty() || matcher.group(3).strip().startsWith("#"))
                levels.add(new Level(indent, matcher.group(2)));
        }
        return levels.stream().map(Level::key).reduce("", (path, key) -> path + "/" + key);
    }

    private record Page(String id, String source, int line, String scope, String keys, String body, String exactPaths) { }
}
