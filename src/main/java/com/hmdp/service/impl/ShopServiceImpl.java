package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CaCheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CaCheClient caCheClient;
    // 建议在类中声明一个静态的线程池专门用于缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void RedisTimeOut(Long id, Long time) throws InterruptedException {
        RedisData rd = new RedisData();
        Shop byId = getById(id);
        rd.setData(byId);
        rd.setTime(LocalDateTime.now().plusSeconds(time));
        //写入redis数据库
        stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX+id,JSONUtil.toJsonStr(rd));
    }

    @Override
    public Result redisByIdReturnShop(Long id) {
        //解决缓存穿透
        Shop shop = shopThrough(id);
        //互斥锁坚决缓存击穿与穿透
//        Shop shop = shopmiutx(id);
        //逻辑过期
//        Shop shop = caCheClient.getRedisTimeOut(SystemConstants.SHOP_PREFIX, id, Shop.class, this::getById, 10L, TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    public boolean lock(String key){
        //如果设置成功就返回true
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "lock", 10, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(lock);
    }

    public void concelLock(String key){
        stringRedisTemplate.delete(key);
    }

    public Shop shopmiutx(Long id){

        String shop = stringRedisTemplate.opsForValue().get(SystemConstants.SHOP_PREFIX + id);
        //有，就直接返回，这里的方法是判断里面有没有具体的值
        Shop byId = null;
        try {
            if(StrUtil.isNotBlank(shop)){
                return JSONUtil.toBean(shop, Shop.class);
            }
//        此时的shop一定是空(也就是下面专门为这种错误id专门设置的一个redis)或者错误的，所以进行判断错误值存不存在
            if(shop != null){
                return null;
            }
            //没有查到就去先尝试拿锁
            boolean lock = lock("lock:shop" + id);
            if(!lock){
                //没拿到，先休眠
                Thread.sleep(50);
                //重试一次
               return shopmiutx(id);
            }
            //成功获得锁就执行查询操作
            byId = getById(id);
            Thread.sleep(200);
            //如果数据库没有就返回null
            if(byId == null){
                //设置一个空值
                stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX + id,"",2,TimeUnit.MINUTES);
                return null;
            }
            //有，先存入redis，然后返回数据
            stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX+id,JSONUtil.toJsonStr(byId),30L,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            concelLock("lock:shop" + id);
        }
        //释放锁

        return byId;


//        //没有，去数据库查询数据
//        Shop byIdShop = getById(id);
//        //数据库没有，返回错误
//

    }

    public Shop shopTimeOut(Long id){
         //从缓存拿到对应的shop信息(默认是预热好的)
        String s = stringRedisTemplate.opsForValue().get(SystemConstants.SHOP_PREFIX + id);
        //如果没有数据一定不存在（预热过的并且没有设置过期时间）
        if(StrUtil.isBlank(s)){
            return null;
        }
        //获取逻辑时间判断是否过期
        RedisData bean = JSONUtil.toBean(s, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) bean.getData(), Shop.class);
        LocalDateTime time = bean.getTime();
        //如果过期就进行重写，否则就直接返回
        if(time.isAfter(LocalDateTime.now())){
            return shop;
        }
        //这一步说明过期了
        //先去获取互斥锁
        boolean lock = lock("lock:Shop" + id);
        //拿到了说明获取成功，否则就返回旧数据先
        if(!lock){
            return shop;
        }
        //拿到锁开始更新
        //防止释放锁的时候有其他线程执行到获取锁那块,进行判定过期时间
        String s1 = stringRedisTemplate.opsForValue().get(SystemConstants.SHOP_PREFIX + id);
        RedisData bean1 = JSONUtil.toBean(s1, RedisData.class);
        Shop shop1 = JSONUtil.toBean((JSONObject) bean1.getData(), Shop.class);
        LocalDateTime time1 = bean1.getTime();
        if(time1.isAfter(LocalDateTime.now())){
            concelLock("lock:Shop" + id);
            return shop1;
        }
        //建立一个新的线程去执行，老线程继续返回老数据,这样就无需等待
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.RedisTimeOut(id, 10L);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    concelLock("lock:Shop" + id);
                }
            });

        return shop;
    }

    public Shop shopThrough(Long id){
        //缓存击穿(大量数据直接绕过redis的解决方案)
        //根据id为key去redis查询
        String shop = stringRedisTemplate.opsForValue().get(SystemConstants.SHOP_PREFIX + id);
        //有，就直接返回，这里的方法是判断里面有没有具体的值
        if(StrUtil.isNotBlank(shop)){
            Shop bean = JSONUtil.toBean(shop, Shop.class);
            return bean;
        }
        //此时的shop一定是空或者错误的，所以进行判断错误值存不存在
        if(shop != null){
            return null;
        }
        //没有，去数据库查询数据
        Shop byIdShop = getById(id);
        //数据库没有，返回错误
        if(byIdShop == null){
            stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX+id," ",2L,TimeUnit.MINUTES);
            return null;
        }
        //有，先存入redis，然后返回数据
        stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX+id,JSONUtil.toJsonStr(byIdShop),30L,TimeUnit.MINUTES);

        return byIdShop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        if (shop.getId() == null) {
           return Result.fail("id不存在");
        }
        updateById(shop);

        stringRedisTemplate.delete(SystemConstants.SHOP_PREFIX+shop.getId());
        return Result.ok();
    }



}
