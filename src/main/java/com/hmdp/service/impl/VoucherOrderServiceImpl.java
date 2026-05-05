package com.hmdp.service.impl;

import com.hmdp.config.RedissionConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.IdReidsWorkers;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
//    @Autowired
//    private VoucherOrderServiceImpl voucherOrderService;
    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;
    @Autowired
    private IdReidsWorkers idReidsWorkers;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Override

    public Result order(Long voucherId) {
        SeckillVoucher vocher = iSeckillVoucherService.getById(voucherId);

        if(vocher == null){
            return Result.fail("优惠不存在！");
        }
        if(vocher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀还没有开始");
        }
        if(LocalDateTime.now().isAfter(vocher.getEndTime())){
            return Result.fail("秒杀已经结束了");
        }
        if(vocher.getStock() <1){
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
//        RedisLock rd = new RedisLock(stringRedisTemplate,"order："+userId);
//        防止一个用户并发访问
        RLock lock = redissonClient.getLock("lock:order" + userId);
        boolean flag = lock.tryLock();
        if (!flag){
            return Result.fail("不可以重复下单");
        }

        try {
            IVoucherOrderService o =(IVoucherOrderService) AopContext.currentProxy();
            return o.getResult(voucherId);
        }
        finally {
            lock.unlock();
        }

    }


    @Transactional
    public Result getResult(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id",userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("只可以购买一次");
        }
        //扣减库存
        boolean DonKnow = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock",0).update();
        if(!DonKnow){
            return Result.fail("库存不足！");
        }

        //生成订单id
        //生成用户id
        //生成优惠卷id
        long orderId = idReidsWorkers.nextId("order");
        Long userid = UserHolder.getUser().getId();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userid);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
