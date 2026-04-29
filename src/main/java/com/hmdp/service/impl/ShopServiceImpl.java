package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
        //没有，去数据库查询数据
        Shop byIdShop = getById(id);
        //没有，返回错误
        if(byIdShop == null){
            return Result.fail("店铺不存在");
        }
        //有，先存入redis，然后返回数据
        stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_PREFIX+id,JSONUtil.toJsonStr(byIdShop));

        return Result.ok(byIdShop);
    }
}
