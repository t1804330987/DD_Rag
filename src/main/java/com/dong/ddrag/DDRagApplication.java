package com.dong.ddrag;

import com.dong.ddrag.ingestion.transformer.ChunkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties({ChunkingProperties.class})
@EnableAsync
@EnableRetry
public class DDRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(DDRagApplication.class, args);
    }
}
