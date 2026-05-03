package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class IdReidsWorkers {

    private  final StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;

    private  IdReidsWorkers(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public  long nextId(String prefix){
        //生成时间搓
        //生成现在时间的秒数
        LocalDateTime now = LocalDateTime.now();
        long TimeStamp = now.toEpochSecond(ZoneOffset.UTC);
        long nowTimeStamp = TimeStamp - BEGIN_TIMESTAMP;

        //生成序列号
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" + data);

        return nowTimeStamp << COUNT_BITS | increment;
    }
}
