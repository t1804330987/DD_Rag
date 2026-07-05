package com.dong.ddrag.ingestion;

import com.dong.ddrag.ingestion.transformer.ChunkingProperties;
import com.dong.ddrag.ingestion.transformer.StructureAwareChunkTransformer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructureAwareChunkTransformerTest {

    @Test
    void shouldPreferMarkdownSectionsAndKeepMetadata() {
        ChunkingProperties properties = new ChunkingProperties(80, 120, 12);
        Document input = Document.builder()
                .text("# 总则\n第一段说明。\n\n## 迭代\n产品团队每两周发布一次。\n每次结束后复盘。")
                .metadata(Map.of("documentId", 3001L, "groupId", 2001L))
                .build();

        List<Document> chunks = new StructureAwareChunkTransformer(properties).apply(List.of(input));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getText()).contains("# 总则");
        assertThat(chunks.get(0).getMetadata()).containsEntry("documentId", 3001L);
        assertThat(chunks.get(0).getMetadata()).containsEntry("groupId", 2001L);
        assertThat(chunks.get(1).getText()).contains("## 迭代");
        assertThat(chunks.get(1).getMetadata()).containsEntry("sectionPath", "迭代");
        assertThat(chunks.get(1).getMetadata()).containsEntry("chunkStrategy", "structure-aware-token-budget-v1");
    }

    @Test
    void shouldIgnoreHeadingsInsideFencedCodeBlocks() {
        ChunkingProperties properties = new ChunkingProperties(80, 120, 12);
        Document input = Document.builder()
                .text("# 总则\n正文说明。\n\n```markdown\n# 示例\n代码块里的标题不应切分。\n```\n\n## 后续\n真实小节内容。")
                .build();

        List<Document> chunks = new StructureAwareChunkTransformer(properties).apply(List.of(input));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(chunk -> chunk.getMetadata().get("sectionPath"))
                .containsExactly("总则", "后续")
                .doesNotContain("示例");
        assertThat(chunks.get(0).getText()).contains("```markdown", "# 示例", "代码块里的标题不应切分。");
        assertThat(chunks.get(1).getText()).contains("## 后续");
    }

    @Test
    void shouldKeepOverlapWithinCurrentSectionBoundary() {
        ChunkingProperties properties = new ChunkingProperties(80, 120, 24);
        Document input = Document.builder()
                .text("# Alpha\n第一节结尾哨兵：ALPHA-END-MARKER。\n\n## Beta\n第二节正文从这里开始。")
                .build();

        List<Document> chunks = new StructureAwareChunkTransformer(properties).apply(List.of(input));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).getMetadata()).containsEntry("sectionPath", "Beta");
        assertThat(chunks.get(1).getText()).startsWith("## Beta");
        assertThat(chunks.get(1).getText()).doesNotContain("ALPHA-END-MARKER");
    }

    @Test
    void shouldRespectMaxTokensForContinuousChineseText() {
        ChunkingProperties properties = new ChunkingProperties(10, 20, 0);
        Document input = Document.builder()
                .text("这是一个没有明显段落和标点的连续中文测试文本用于验证切片上限确实会被执行而不是被粗略估算放大")
                .build();

        List<Document> chunks = new StructureAwareChunkTransformer(properties).apply(List.of(input));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.getText()).hasSizeLessThanOrEqualTo(20));
    }
}
