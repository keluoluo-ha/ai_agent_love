package com.hhk.aiagentlove.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppTest {

    @Resource
    private LoveApp loveApp;

    @Test
    void test1() {


        System.out.println("---------------------------------------------------------------------------");
        String res = loveApp.doChat("现在几点了", "1");
        System.out.println(res);


    }
    @Test
    void doChatWithRag() {
        // 测试联网搜索问题的答案
        testMessage("如何在恋爱中与对方有效沟通未来规划");


    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithRag(message,chatId);
        Assertions.assertNotNull(answer);
    }

}