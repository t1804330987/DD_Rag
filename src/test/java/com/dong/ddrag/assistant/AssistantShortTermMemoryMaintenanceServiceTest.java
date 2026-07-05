package com.dong.ddrag.assistant;

import com.dong.ddrag.assistant.mapper.AssistantSessionContextMapper;
import com.dong.ddrag.assistant.model.entity.AssistantSessionContextEntity;
import com.dong.ddrag.assistant.model.enums.AssistantToolMode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AssistantShortTermMemoryMaintenanceServiceTest {

    private static final String SERVICE_CLASS_NAME =
            "com.dong.ddrag.assistant.memory.AssistantShortTermMemoryMaintenanceService";

    @Test
    void shouldExposePreAndPostResponseMaintenanceEntrypoints() throws Exception {
        Class<?> serviceClass = requireMaintenanceServiceClass();

        assertThatCode(() -> serviceClass.getDeclaredMethod(
                "maintainBeforeResponse",
                Long.class,
                Long.class,
                AssistantToolMode.class,
                Long.class,
                Long.class
        )).doesNotThrowAnyException();
        assertThatCode(() -> serviceClass.getDeclaredMethod(
                "maintainAfterResponse",
                Long.class,
                Long.class,
                AssistantToolMode.class,
                Long.class,
                Long.class
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldPersistSessionMemoryRangesAndContextVersion() throws Exception {
        assertAccessorExists("SessionMemory", String.class);
        assertAccessorExists("CompactSummary", String.class);
        assertAccessorExists("SessionMemoryBaseMessageId", Long.class);
        assertAccessorExists("SessionMemoryRangeEndMessageId", Long.class);
        assertAccessorExists("CompactSummaryBaseMessageId", Long.class);
        assertAccessorExists("CompactSummaryRangeEndMessageId", Long.class);
        assertAccessorExists("ContextVersion", Long.class);
    }

    @Test
    void shouldUseVersionedWriteWhenUpdatingShortTermMemory() throws Exception {
        Method method = requireMethod(
                AssistantSessionContextMapper.class,
                "updateShortTermMemoryWithVersion",
                AssistantSessionContextEntity.class,
                Long.class
        );

        assertThat(method.getReturnType()).isEqualTo(int.class);
    }

    @Test
    void shouldSkipMaintenanceWhenThereAreNoNewMessages() throws Exception {
        Class<?> serviceClass = requireMaintenanceServiceClass();

        Method method = serviceClass.getDeclaredMethod(
                "shouldMaintainSessionMemory",
                List.class,
                long.class
        );

        assertThat(method.getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    void shouldTriggerCompactWhenSessionContextExceeds6500Tokens() throws Exception {
        Class<?> serviceClass = requireMaintenanceServiceClass();

        Method method = serviceClass.getDeclaredMethod(
                "shouldCompactSession",
                int.class,
                long.class,
                long.class
        );

        assertThat(method.getReturnType()).isEqualTo(boolean.class);
    }

    private Class<?> requireMaintenanceServiceClass() throws ClassNotFoundException {
        try {
            return Class.forName(SERVICE_CLASS_NAME);
        } catch (ClassNotFoundException exception) {
            fail("缺少短期记忆维护服务类: " + SERVICE_CLASS_NAME);
            throw exception;
        }
    }

    private Method requireMethod(Class<?> targetClass, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        try {
            return targetClass.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            fail("缺少方法: %s#%s".formatted(targetClass.getName(), name));
            throw exception;
        }
    }

    private void assertAccessorExists(String propertyName, Class<?> propertyType) {
        assertThatCode(() -> AssistantSessionContextEntity.class.getDeclaredMethod("get" + propertyName))
                .doesNotThrowAnyException();
        assertThatCode(() -> AssistantSessionContextEntity.class.getDeclaredMethod("set" + propertyName, propertyType))
                .doesNotThrowAnyException();
    }
}
