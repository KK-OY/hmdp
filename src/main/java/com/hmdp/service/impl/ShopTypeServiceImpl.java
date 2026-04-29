package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IShopTypeService typeService;
    @Override
    public Result redisGetList() {
        //先把数据查出来
        String s = stringRedisTemplate.opsForValue().get(SystemConstants.SHOP_LIST);
        //先判断有没有在rdeis
        //有就直接返回
        if (StrUtil.isNotBlank(s)){
            List<ShopType> bean = JSONUtil.toList(s, ShopType.class);
            return Result.ok(bean);
        }
        // 没有就去数据库查数据
            List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        //封装进redis
        stringRedisTemplate.opsForValue().set(SystemConstants.SHOP_LIST, JSONUtil.toJsonStr(typeList));
        //返回数据
        return Result.ok(typeList);

    }
}
