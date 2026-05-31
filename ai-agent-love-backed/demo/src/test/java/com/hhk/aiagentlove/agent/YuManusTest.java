package com.hhk.aiagentlove.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest
class YuManusTest {

    @Resource
    private YuManus yuManus;

    @Test
    void run() {
        String userPrompt = """
                我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点""";
        SseEmitter emitter = yuManus.startSse(userPrompt, null, null, null);
        org.junit.jupiter.api.Assertions.assertNotNull(emitter);
    }
}
