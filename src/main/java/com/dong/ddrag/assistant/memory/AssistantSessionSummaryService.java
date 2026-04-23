package com.dong.ddrag.assistant.memory;

import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.model.entity.AssistantMessageEntity;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.enums.AssistantMessageRole;
import com.dong.ddrag.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AssistantSessionSummaryService {

    private static final int SUMMARY_SOURCE_CHAR_LIMIT = 2000;
    private static final int TOKEN_ESTIMATE_DIVISOR = 4;

    private final AssistantSessionContextMapper assistantSessionContextMapper;
    private final Clock clock;
    private final int messageThreshold;
    private final int tokenThreshold;
    private final int staleDays;

    @Autowired
    public AssistantSessionSummaryService(
            AssistantSessionContextMapper assistantSessionContextMapper,
            @Value("${assistant.session.summary.message-threshold:20}") int messageThreshold,
            @Value("${assistant.session.summary.token-threshold:8000}") int tokenThreshold,
            @Value("${assistant.session.summary.stale-days:7}") int staleDays
    ) {
        this(assistantSessionContextMapper, Clock.systemDefaultZone(), messageThreshold, tokenThreshold, staleDays);
    }

    public AssistantSessionSummaryService(
            AssistantSessionContextMapper assistantSessionContextMapper,
            Clock clock,
            int messageThreshold,
            int tokenThreshold,
            int staleDays
    ) {
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.clock = clock;
        this.messageThreshold = messageThreshold;
        this.tokenThreshold = tokenThreshold;
        this.staleDays = staleDays;
    }

    public String loadReusableSummary(Long sessionId, LocalDateTime sessionLastMessageAt) {
        AssistantSessionContextEntity existingSummary = assistantSessionContextMapper.selectBySessionId(sessionId);
        if (existingSummary == null || existingSummary.getSummaryText() == null || existingSummary.getSummaryText().isBlank()) {
            return null;
        }
        if (isStale(existingSummary.getUpdatedAt(), sessionLastMessageAt)) {
            return null;
        }
        return existingSummary.getSummaryText();
    }

    public boolean shouldSummarize(long totalMessages, int estimatedTokens, LocalDateTime sessionLastMessageAt) {
        if (totalMessages > messageThreshold || estimatedTokens > tokenThreshold) {
            return true;
        }
        if (sessionLastMessageAt == null) {
            return false;
        }
        return ChronoUnit.DAYS.between(sessionLastMessageAt, LocalDateTime.now(clock)) > staleDays;
    }

    public String summarizeAndPersist(Long sessionId, List<AssistantMessageEntity> messages, int recentMessageLimit) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        int keepRecentCount = Math.max(1, recentMessageLimit);
        int summaryMessageCount = Math.max(0, messages.size() - keepRecentCount);
        if (summaryMessageCount == 0) {
            return null;
        }
        List<AssistantMessageEntity> messagesForSummary = messages.subList(0, summaryMessageCount);
        String summaryText = buildSummaryText(messagesForSummary);
        AssistantSessionContextEntity entity = new AssistantSessionContextEntity();
        entity.setSessionId(sessionId);
        entity.setSummaryText(summaryText);
        entity.setSourceMessageId(messagesForSummary.getLast().getId());
        entity.setUpdatedAt(LocalDateTime.now(clock));
        int affectedRows = assistantSessionContextMapper.upsert(entity);
        if (affectedRows != 1) {
            throw new BusinessException("保存会话摘要失败");
        }
        return summaryText;
    }

    public int estimateTokens(List<AssistantMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int totalChars = messages.stream()
                .map(AssistantMessageEntity::getContent)
                .filter(content -> content != null && !content.isBlank())
                .mapToInt(String::length)
                .sum();
        return Math.max(1, totalChars / TOKEN_ESTIMATE_DIVISOR);
    }

    private boolean isStale(LocalDateTime summaryUpdatedAt, LocalDateTime sessionLastMessageAt) {
        if (summaryUpdatedAt == null) {
            return true;
        }
        if (sessionLastMessageAt != null && summaryUpdatedAt.isBefore(sessionLastMessageAt)) {
            return true;
        }
        return ChronoUnit.DAYS.between(summaryUpdatedAt, LocalDateTime.now(clock)) > staleDays;
    }

    private String buildSummaryText(List<AssistantMessageEntity> messages) {
        StringBuilder builder = new StringBuilder("历史摘要:").append(System.lineSeparator());
        int currentChars = builder.length();
        for (AssistantMessageEntity message : messages) {
            String line = "- %s：%s".formatted(roleLabel(message.getRole()), normalizeContent(message.getContent()));
            if (currentChars + line.length() > SUMMARY_SOURCE_CHAR_LIMIT) {
                builder.append("- 其余历史消息已省略").append(System.lineSeparator());
                break;
            }
            builder.append(line).append(System.lineSeparator());
            currentChars = builder.length();
        }
        return builder.toString().trim();
    }

    private String roleLabel(String role) {
        return AssistantMessageRole.USER.name().equals(role) ? "用户" : "助手";
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 160) + "...";
    }
}
