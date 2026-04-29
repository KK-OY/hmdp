package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Override
    public Result redisByIdReturnShop(Long id) {
        //根据id为key去redis查询
        String shop = stringRedisTemplate.opsForValue().get(SystemConstants.SHOP_PREFIX + id);
        //有，就直接返回
        if(StrUtil.isNotBlank(shop)){
            Shop bean = JSONUtil.toBean(shop, Shop.class);
            return Result.ok(bean);
        }
        //此时的shop一定是不存在或者错误的，所以进行判断错误值存不存在
        if(shop != null){
            return Result.fail("店铺不存在");
        }
        //没有，去数据库查询数据
        Shop byIdShop = getById(id);
        //没有，返回错误
        if(byIdShop == null){
            stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX+id," ",2L,TimeUnit.MINUTES);
            return Result.fail("店铺没有啦啦啦");
        }
        //有，先存入redis，然后返回数据
        stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX+id,JSONUtil.toJsonStr(byIdShop),30L,TimeUnit.MINUTES);

        return Result.ok(byIdShop);
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
