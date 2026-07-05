package com.dong.ddrag.assistant.memory.runtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AssistantRuntimeMemoryRenderer {

    private static final int DEFAULT_MAX_CHARS = 2000;

    public String render(AssistantRuntimeMemoryState state) {
        return render(state, DEFAULT_MAX_CHARS);
    }

    public String render(AssistantRuntimeMemoryState state, int maxChars) {
        if (state == null || state.conclusions().isEmpty()) {
            return null;
        }
        List<String> activeLines = new ArrayList<>();
        List<String> historyLines = new ArrayList<>();
        for (AssistantRuntimeMemoryState.Conclusion conclusion : state.conclusions()) {
            if (conclusion == null || isBlank(conclusion.keyLabel())) {
                continue;
            }
            if (isBlank(conclusion.activeValue())) {
                activeLines.add("- " + conclusion.keyLabel() + " 已废弃，暂无替代");
            } else {
                activeLines.add("- " + conclusion.keyLabel() + " = " + conclusion.activeValue());
            }
            for (AssistantRuntimeMemoryState.SupersededValue superseded : conclusion.supersededValues()) {
                if (superseded != null && !isBlank(superseded.value())) {
                    historyLines.add("- " + conclusion.keyLabel() + " 废弃值: " + superseded.value());
                }
            }
        }
        if (activeLines.isEmpty()) {
            return null;
        }
        String full = buildBlock(activeLines, historyLines);
        if (maxChars <= 0 || full.length() <= maxChars) {
            return full;
        }
        return buildBlock(activeLines, List.of());
    }

    private String buildBlock(List<String> activeLines, List<String> historyLines) {
        StringBuilder builder = new StringBuilder();
        builder.append("runtime memory").append(System.lineSeparator());
        builder.append("以下是当前会话内用户最新有效结论；若它与摘要或历史消息冲突，以本块为准。");
        builder.append(System.lineSeparator()).append("当前有效结论:");
        for (String line : activeLines) {
            builder.append(System.lineSeparator()).append(line);
        }
        if (!historyLines.isEmpty()) {
            builder.append(System.lineSeparator()).append("已废弃/被替换的旧值:");
            for (String line : historyLines) {
                builder.append(System.lineSeparator()).append(line);
            }
        }
        return builder.toString().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
