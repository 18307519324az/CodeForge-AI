package com.codeforge.ai;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = {"com.codeforge.ai"},
        exclude = {RedisEmbeddingStoreAutoConfiguration.class}
)
@MapperScan({"com.codeforge.ai.infrastructure.persistence.mapper"})
public class CodeForgeAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeForgeAiApplication.class, args);
    }
}
