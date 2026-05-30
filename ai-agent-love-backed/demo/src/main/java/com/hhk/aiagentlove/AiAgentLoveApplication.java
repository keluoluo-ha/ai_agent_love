package com.hhk.aiagentlove;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;


@MapperScan("com.hhk.aiagentlove.mapper")
@SpringBootApplication(exclude = {PgVectorStoreAutoConfiguration.class})
public class AiAgentLoveApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentLoveApplication.class, args);
    }

}
