package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(!RegexUtils.isPhoneInvalid(phone)){
            //如果错误，返回失败
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
       stringRedisTemplate.opsForValue().set(phone,code,2L,TimeUnit.MINUTES);
        //发送验证码
        log.info("已保存的验证码{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
       //验证验证码是否正确
        String code = stringRedisTemplate.opsForValue().get(loginForm.getPhone());
        //错误就打回
        if(code == null || !loginForm.getCode().equals(code)){
            log.info("正确的验证码{}",code);
            return Result.fail("验证码错误!!");
        }
        if(!RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误");
        }
        //正确就判断手机号是否存在数据库
        User user = query().eq("phone", loginForm.getPhone()).one();
        //不存在就注册一个用户
        if(user == null){
          user = creatUserWithPhone(loginForm.getPhone());
        }
        //存在就登录成功
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));

        //随机生成token
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //将user转为map对象(使所有的非str类型转为str值)
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>()
                , CopyOptions.create().setFieldValueEditor((filename,filevalue) -> filevalue.toString()));
        //将user对象存入redishash
        stringRedisTemplate.opsForHash().putAll(SystemConstants.USER_TOKEN+token,userMap);
        //设置token有效期
        stringRedisTemplate.expire(SystemConstants.USER_TOKEN+token,3000, TimeUnit.MINUTES);
        //将token返回给前端
        return Result.ok(token);
    }

    @Override
    public Result me() {

        return Result.ok(UserHolder.getUser());
    }

    @Override
    public void logout(String token) {
        if(token != null){
            stringRedisTemplate.delete(SystemConstants.USER_TOKEN+token);
        }
    }

    private User creatUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
        //保存到数据库
        save(user);
        return user;
    }
}
