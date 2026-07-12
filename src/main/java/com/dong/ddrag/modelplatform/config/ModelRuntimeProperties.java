package com.dong.ddrag.modelplatform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "ddrag.model-runtime")
public class ModelRuntimeProperties {

    @Valid
    private final Assistant assistant = new Assistant();
    @Valid
    private final Invocation invocation = new Invocation();
    @Valid
    private final Stream stream = new Stream();
    @Valid
    private final Timeout timeout = new Timeout();

    public Assistant getAssistant() { return assistant; }
    public Invocation getInvocation() { return invocation; }
    public Stream getStream() { return stream; }
    public Timeout getTimeout() { return timeout; }

    public static class Assistant {
        @Min(1)
        private int maxActiveTurns = 50;

        public int getMaxActiveTurns() { return maxActiveTurns; }
        public void setMaxActiveTurns(int maxActiveTurns) { this.maxActiveTurns = maxActiveTurns; }
    }

    public static class Invocation {
        @Min(1)
        private int globalLimit = 50;
        @Min(1)
        private int perUserLimit = 2;
        @Min(1)
        private int defaultConnectionLimit = 50;

        public int getGlobalLimit() { return globalLimit; }
        public void setGlobalLimit(int globalLimit) { this.globalLimit = globalLimit; }
        public int getPerUserLimit() { return perUserLimit; }
        public void setPerUserLimit(int perUserLimit) { this.perUserLimit = perUserLimit; }
        public int getDefaultConnectionLimit() { return defaultConnectionLimit; }
        public void setDefaultConnectionLimit(int defaultConnectionLimit) { this.defaultConnectionLimit = defaultConnectionLimit; }
    }

    public static class Stream {
        @Min(1)
        private int executorThreads = 50;
        @Min(0)
        @Max(0)
        private int queueCapacity;

        public int getExecutorThreads() { return executorThreads; }
        public void setExecutorThreads(int executorThreads) { this.executorThreads = executorThreads; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    }

    public static class Timeout {
        @NotNull
        private Duration firstToken = Duration.ofSeconds(60);
        @NotNull
        private Duration idle = Duration.ofSeconds(45);
        @NotNull
        private Duration businessTotal = Duration.ofMinutes(5);
        @NotNull
        private Duration transportHard = Duration.ofMinutes(6);

        public Duration getFirstToken() { return firstToken; }
        public void setFirstToken(Duration firstToken) { this.firstToken = firstToken; }
        public Duration getIdle() { return idle; }
        public void setIdle(Duration idle) { this.idle = idle; }
        public Duration getBusinessTotal() { return businessTotal; }
        public void setBusinessTotal(Duration businessTotal) { this.businessTotal = businessTotal; }
        public Duration getTransportHard() { return transportHard; }
        public void setTransportHard(Duration transportHard) { this.transportHard = transportHard; }

        @AssertTrue(message = "all model runtime timeouts must be positive")
        public boolean areAllTimeoutsPositive() {
            return isPositive(firstToken) && isPositive(idle)
                    && isPositive(businessTotal) && isPositive(transportHard);
        }

        @AssertTrue(message = "transport hard timeout must be greater than business total timeout")
        public boolean isTransportHardAfterBusinessTotal() {
            return businessTotal == null || transportHard == null || transportHard.compareTo(businessTotal) > 0;
        }

        private boolean isPositive(Duration duration) {
            return duration == null || (!duration.isZero() && !duration.isNegative());
        }
    }
}
