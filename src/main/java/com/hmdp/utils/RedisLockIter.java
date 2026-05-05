package com.hmdp.utils;

public interface RedisLockIter {

    public Boolean tryLock(Long timeOut);

    public void unlock();
}
