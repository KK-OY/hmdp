package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@AllArgsConstructor
@NoArgsConstructor
public class RedreshIterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if(token == null){
            return true;
        }
        if(!StrUtil.isBlank(token)){
            //根据建把值转化为map
            Map<Object, Object> usermap = stringRedisTemplate.opsForHash().entries(SystemConstants.USER_TOKEN+token);
            if(!usermap.isEmpty()){
                UserDTO user = BeanUtil.fillBeanWithMap(usermap, new UserDTO(), false);
                UserHolder.saveUser(user);
                stringRedisTemplate.expire(SystemConstants.USER_TOKEN+token,3000, TimeUnit.MINUTES);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       UserHolder.removeUser();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
