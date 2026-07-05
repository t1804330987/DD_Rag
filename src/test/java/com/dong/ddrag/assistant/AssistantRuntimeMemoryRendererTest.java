package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryState;
import com.dong.ddrag.assistant.memory.runtime.AssistantRuntimeMemoryRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRuntimeMemoryRendererTest {

    private final AssistantRuntimeMemoryRenderer renderer = new AssistantRuntimeMemoryRenderer();

    @Test
    void shouldReturnNullForEmptyState() {
        assertThat(renderer.render(AssistantRuntimeMemoryState.empty(), 1000)).isNull();
    }

    @Test
    void shouldRenderActiveAndRevokedConclusions() {
        AssistantRuntimeMemoryState state = new AssistantRuntimeMemoryState(
                1L,
                List.of(
                        new AssistantRuntimeMemoryState.Conclusion("rm_1", "论文选题", "方案 B", 3002L, 2000L, List.of(
                                new AssistantRuntimeMemoryState.SupersededValue("方案 A", 3001L, 2000L, 3002L, "替换")
                        )),
                        new AssistantRuntimeMemoryState.Conclusion("rm_2", "实验方法", null, null, null, List.of(
                                new AssistantRuntimeMemoryState.SupersededValue("方法 X", 3003L, 3004L, 3004L, "用户撤销")
                        ))
                ),
                null
        );

        String block = renderer.render(state, 1000);

        assertThat(block).contains("runtime memory");
        assertThat(block).contains("论文选题 = 方案 B");
        assertThat(block).contains("实验方法 已废弃，暂无替代");
        assertThat(block).contains("废弃值");
    }

    @Test
    void shouldTrimSupersededNotesBeforeActiveValues() {
        AssistantRuntimeMemoryState state = new AssistantRuntimeMemoryState(
                1L,
                List.of(new AssistantRuntimeMemoryState.Conclusion(
                        "rm_1",
                        "论文选题",
                        "方案 B",
                        3002L,
                        2000L,
                        List.of(new AssistantRuntimeMemoryState.SupersededValue(
                                "很长很长很长很长很长很长的旧方案",
                                3001L,
                                2000L,
                                3002L,
                                "替换"
                        ))
                )),
                null
        );

        String block = renderer.render(state, 60);

        assertThat(block).contains("论文选题 = 方案 B");
        assertThat(block).doesNotContain("很长很长很长");
    }
}
