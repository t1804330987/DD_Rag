package com.dong.ddrag.assistant.memory.runtime;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class AssistantRuntimeMemoryStateApplier {

    private static final int SUPERSEDED_LIMIT = 3;

    public AssistantRuntimeMemoryState apply(
            AssistantRuntimeMemoryState state,
            List<AssistantRuntimeMemoryChange> changes,
            Long sourceMessageId,
            Long now
    ) {
        return apply(state, changes, sourceMessageId, now, null);
    }

    public AssistantRuntimeMemoryState apply(
            AssistantRuntimeMemoryState state,
            List<AssistantRuntimeMemoryChange> changes,
            Long sourceMessageId,
            Long now,
            String originalUserRequest
    ) {
        AssistantRuntimeMemoryState current = state == null ? AssistantRuntimeMemoryState.empty() : state;
        if (changes == null || changes.isEmpty()) {
            return current;
        }
        List<AssistantRuntimeMemoryState.Conclusion> conclusions = new ArrayList<>(current.conclusions());
        AssistantRuntimeMemoryState.Pending pending = null;
        boolean changed = false;
        for (AssistantRuntimeMemoryChange change : changes) {
            if (change == null || change.action() == null || change.action() == AssistantRuntimeMemoryAction.NOOP) {
                continue;
            }
            switch (change.action()) {
                case ADD -> {
                    conclusions.add(new AssistantRuntimeMemoryState.Conclusion(
                            generateKeyId(conclusions),
                            normalize(change.keyLabel()),
                            normalize(change.value()),
                            sourceMessageId,
                            now,
                            List.of()
                    ));
                    changed = true;
                }
                case REPLACE -> changed = replace(conclusions, change, sourceMessageId, now) || changed;
                case REVOKE -> changed = revoke(conclusions, change, sourceMessageId, now) || changed;
                case ASK_CONFIRMATION -> {
                    pending = new AssistantRuntimeMemoryState.Pending(
                            AssistantRuntimeMemoryAction.ASK_CONFIRMATION,
                            normalize(change.targetKeyId()),
                            normalize(change.keyLabel()),
                            normalize(change.value()),
                            sourceMessageId,
                            normalize(originalUserRequest),
                            normalize(change.confirmationQuestion()),
                            now
                    );
                    changed = true;
                }
                case NOOP -> {
                }
            }
        }
        if (!changed) {
            return current;
        }
        return new AssistantRuntimeMemoryState(current.version() + 1, List.copyOf(conclusions), pending);
    }

    public AssistantRuntimeMemoryState clearPending(AssistantRuntimeMemoryState state) {
        AssistantRuntimeMemoryState current = state == null ? AssistantRuntimeMemoryState.empty() : state;
        if (current.pending() == null) {
            return current;
        }
        return new AssistantRuntimeMemoryState(current.version() + 1, current.conclusions(), null);
    }

    public AssistantRuntimeMemoryState applyPending(
            AssistantRuntimeMemoryState state,
            Long sourceMessageId,
            Long now
    ) {
        AssistantRuntimeMemoryState current = state == null ? AssistantRuntimeMemoryState.empty() : state;
        AssistantRuntimeMemoryState.Pending pending = current.pending();
        if (pending == null) {
            return current;
        }
        AssistantRuntimeMemoryChange change = new AssistantRuntimeMemoryChange(
                AssistantRuntimeMemoryAction.REPLACE,
                pending.targetKeyId(),
                pending.proposedKeyLabel(),
                pending.proposedValue(),
                "CONFIRMED",
                null
        );
        AssistantRuntimeMemoryState withoutPending = new AssistantRuntimeMemoryState(
                current.version(),
                current.conclusions(),
                null
        );
        return apply(withoutPending, List.of(change), sourceMessageId, now);
    }

    private boolean replace(
            List<AssistantRuntimeMemoryState.Conclusion> conclusions,
            AssistantRuntimeMemoryChange change,
            Long sourceMessageId,
            Long now
    ) {
        int index = findIndex(conclusions, change.targetKeyId());
        if (index < 0 || isBlank(change.value())) {
            return false;
        }
        AssistantRuntimeMemoryState.Conclusion existing = conclusions.get(index);
        List<AssistantRuntimeMemoryState.SupersededValue> superseded = prependSuperseded(
                existing,
                sourceMessageId,
                now,
                normalize(change.reason())
        );
        conclusions.set(index, new AssistantRuntimeMemoryState.Conclusion(
                existing.keyId(),
                existing.keyLabel(),
                normalize(change.value()),
                sourceMessageId,
                now,
                superseded
        ));
        return true;
    }

    private boolean revoke(
            List<AssistantRuntimeMemoryState.Conclusion> conclusions,
            AssistantRuntimeMemoryChange change,
            Long sourceMessageId,
            Long now
    ) {
        int index = findIndex(conclusions, change.targetKeyId());
        if (index < 0) {
            return false;
        }
        AssistantRuntimeMemoryState.Conclusion existing = conclusions.get(index);
        List<AssistantRuntimeMemoryState.SupersededValue> superseded = prependSuperseded(
                existing,
                sourceMessageId,
                now,
                normalize(change.reason())
        );
        conclusions.set(index, new AssistantRuntimeMemoryState.Conclusion(
                existing.keyId(),
                existing.keyLabel(),
                null,
                null,
                null,
                superseded
        ));
        return true;
    }

    private List<AssistantRuntimeMemoryState.SupersededValue> prependSuperseded(
            AssistantRuntimeMemoryState.Conclusion existing,
            Long sourceMessageId,
            Long now,
            String reason
    ) {
        List<AssistantRuntimeMemoryState.SupersededValue> values = new ArrayList<>();
        if (!isBlank(existing.activeValue())) {
            values.add(new AssistantRuntimeMemoryState.SupersededValue(
                    existing.activeValue(),
                    existing.activeSourceMessageId(),
                    now,
                    sourceMessageId,
                    reason
            ));
        }
        values.addAll(existing.supersededValues());
        return values.stream().limit(SUPERSEDED_LIMIT).toList();
    }

    private int findIndex(List<AssistantRuntimeMemoryState.Conclusion> conclusions, String keyId) {
        if (isBlank(keyId)) {
            return -1;
        }
        for (int index = 0; index < conclusions.size(); index++) {
            if (keyId.equals(conclusions.get(index).keyId())) {
                return index;
            }
        }
        return -1;
    }

    private String generateKeyId(List<AssistantRuntimeMemoryState.Conclusion> conclusions) {
        int max = conclusions.stream()
                .map(AssistantRuntimeMemoryState.Conclusion::keyId)
                .filter(keyId -> keyId != null && keyId.startsWith("rm_"))
                .map(keyId -> keyId.substring(3))
                .flatMap(value -> {
                    try {
                        return java.util.stream.Stream.of(Integer.parseInt(value));
                    } catch (NumberFormatException exception) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .max(Comparator.naturalOrder())
                .orElse(0);
        return "rm_" + (max + 1);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
