package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Data
@Component
public class CaCheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CaCheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //核心方法=======================================================

    public void set(String key, Object value, Long time, TimeUnit t){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,t);
    }

    public void setTimeOut(String key, Object value, Long time, TimeUnit t){
        RedisData rd = new RedisData();
        rd.setTime(LocalDateTime.now().plusSeconds(t.toSeconds(time)));
        rd.setData(value);
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(rd));
    }

    public <R,ID> R getRedisThrough(String keyfix, ID id, Class<R> ty, Function<ID,R> dbfallFauction,Long time, TimeUnit t){
        String keyprfix = keyfix+id;
        String r = stringRedisTemplate.opsForValue().get(keyfix);
        //有，就直接返回，这里的方法是判断里面有没有具体的值
            if(StrUtil.isNotBlank(r)){
                return JSONUtil.toBean(r, ty);
            }
//        此时的shop一定是空或者错误的，所以进行判断错误值存不存在
            if(r != null){
                return null;
            }
            //成功获得锁就执行查询操作
        R apply = dbfallFauction.apply(id);
        //如果数据库没有就返回null
            if(apply == null){
                //设置一个空值
                stringRedisTemplate.opsForValue().set(keyprfix,"",2,TimeUnit.MINUTES);
                return null;
            }
            //有，先存入redis，然后返回数据
            this.set(keyprfix,apply,time,t);
        //释放锁

        return apply;
    }

    public <R,ID> R getRedisTimeOut(String keyfix, ID id, Class<R> ty, Function<ID,R> dbfallFauction,Long time, TimeUnit t){
        String prefix = keyfix+id;
        //从缓存拿到对应的shop信息(默认是预热好的)
       String r =  stringRedisTemplate.opsForValue().get(prefix);
        //如果没有数据一定不存在（预热过的并且没有设置过期时间）
        if(StrUtil.isBlank(r)){
            return null;
        }
        //获取逻辑时间判断是否过期
        RedisData bean = JSONUtil.toBean(r, RedisData.class);
        R r1 = JSONUtil.toBean((JSONObject) bean.getData(), ty);
        LocalDateTime time1 = bean.getTime();
        //如果没过期就直接返回
        if(time1.isAfter(LocalDateTime.now())){
            return r1;
        }
        //这一步说明过期了,要重写
        //先去获取互斥锁
        boolean lock = lock("lock:Shop" + id);
        //拿到了说明获取成功，否则就返回旧数据先
        if(!lock){
            return r1;
        }
        //拿到锁开始更新
        //防止释放锁的时候有其他线程执行到获取锁那块,进行判定过期时间
        String s2 = stringRedisTemplate.opsForValue().get(prefix);
        RedisData bean1 = JSONUtil.toBean(s2, RedisData.class);
        R  R1 = JSONUtil.toBean((JSONObject) bean1.getData(), ty);
        LocalDateTime time2 = bean1.getTime();
        if(time2.isAfter(LocalDateTime.now())){
            concelLock("lock:Shop" + id);
            return R1;
        }
        //建立一个新的线程去执行，老线程继续返回老数据,这样就无需等待
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                R apply = dbfallFauction.apply(id);
                this.setTimeOut(prefix,apply,time,t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                concelLock("lock:Shop" + id);
            }
        });

        return r1;
    }

    //核心方法=======================================================

    public boolean lock(String key){
        //如果设置成功就返回true
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(lock);
    }

    public void concelLock(String key){
        stringRedisTemplate.delete(key);
    }
}
