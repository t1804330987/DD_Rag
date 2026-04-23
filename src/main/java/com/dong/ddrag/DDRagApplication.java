package com.dong.ddrag;

import com.dong.ddrag.ingestion.transformer.ChunkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ChunkingProperties.class)
public class DDRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(DDRagApplication.class, args);
    }
}
