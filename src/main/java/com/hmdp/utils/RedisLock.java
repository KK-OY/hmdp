package com.hmdp.utils;

import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

//创建对象自动传入redis
@AllArgsConstructor
public class RedisLock implements RedisLockIter {

    private final static String uuID = UUID.randomUUID().toString();
    private StringRedisTemplate stringRedisTemplate;
    String LOCK_primary ;

    @Override
    public Boolean tryLock( Long timeOut) {
        //value是uuid+线程id拼接，保证一定的唯一 ，相当于酒店+房间号的拼接  （uuid每台机器生成的不同，线程id保证不会有）
        String threadId =  Thread.currentThread().getId() + uuID;
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent("lock:" + LOCK_primary, threadId, timeOut, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(flag);
    }

    @Override
    public void unlock() {
        String threadId =  Thread.currentThread().getId()+uuID;
        String id = stringRedisTemplate.opsForValue().get("lock:" + LOCK_primary);

        if(threadId.equals(id)){
            stringRedisTemplate.delete("lock:" + LOCK_primary);
        }

    }
}
