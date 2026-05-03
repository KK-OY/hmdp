package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.IdReidsWorkers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class testRedis {
    @Autowired
    private  ShopServiceImpl service;

    @Resource
    private IdReidsWorkers idReidsWorkers;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void test() throws InterruptedException {
        service.RedisTimeOut(2L,10L);
    }

    @Test
    void test2() throws InterruptedException {
        // 300 个并发任务
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                // 正式压测时，把返回的 ID 丢弃即可，不要打印，否则严重拖慢速度
                long id = idReidsWorkers.nextId("testgo");
            }
            // 当前任务的 100 个 ID 生成完毕，报告完成
            latch.countDown();
        };

        long BEGIN = System.currentTimeMillis();

        // 瞬间提交 300 个任务到线程池
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 【最核心的一步】：主线程在这里死等，直到 latch 减到 0（即所有任务完成）
        latch.await();

        long END = System.currentTimeMillis();

        System.out.println("成功生成 30000 个全局唯一ID！");
        System.out.println("总耗时: " + (END - BEGIN) + " 毫秒");
    }

}
