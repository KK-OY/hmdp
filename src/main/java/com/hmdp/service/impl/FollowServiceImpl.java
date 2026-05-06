package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserServiceImpl userService;

    @Override
    public Result isFollow(Long id, boolean isFollow) {
        //添加关系表  关注者当前id，被关注传入id
        //判断 isfollow ，true就添加，否则删除
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            //添加对应表
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            boolean save = save(follow);
            if (save) {
                stringRedisTemplate.opsForSet().add("Folow:" + userId.toString(), id.toString());
            }
        } else {
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if (remove) {
                stringRedisTemplate.opsForSet().remove("Folow:" + userId.toString(), id.toString());
            }
            //删除对应表
        }
        return Result.ok();
    }

    @Override
    public Result orFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        //查询当前用户，以及对应用户的关注状态
        Boolean member = stringRedisTemplate.opsForSet().isMember("Folow:" + userId.toString(), id.toString());

        return Result.ok(member);

    }

    @Override
    public Result common(Long id) {
        Long userId = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("Folow:" +userId.toString(), "Folow:" +id.toString());
        if(intersect.isEmpty()){
            return Result.ok("无共同关注");
        }
        List<Long> ids = intersect.stream().map(s -> Long.valueOf(s)).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream().map(u -> BeanUtil.copyProperties(u, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(users);
    }
}
