package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScorllResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Override
    public Result GetById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
        return  Result.fail("博客不存在!!");
        }
        extracted(blog);
        isBlogLike(blog);
        return Result.ok(blog);
    }


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(
                 blog -> {
                    this.extracted(blog);
                    this.isBlogLike(blog);
                }
        );
        return Result.ok(records);
    }

    @Override
    public Result isLike(Long id) {
        Blog blog = getById(id);
        Long userId = UserHolder.getUser().getId();
//判断id有没有出现在set集合里
        Double member = stringRedisTemplate.opsForZSet().score(SystemConstants.is_Like + id.toString(), userId.toString());

        if(member == null){
            //数据库更新
            boolean boolen = update().setSql("liked = liked +1").eq("id", id).update();

            if(boolen){
                stringRedisTemplate.opsForZSet().add(SystemConstants.is_Like+ id,userId.toString(), System.currentTimeMillis());
            }

        }else {
            //数据库更新
            boolean boolen = update().setSql("liked = liked - 1").eq("id", id).update();

            if(boolen){
                stringRedisTemplate.opsForZSet().remove(SystemConstants.is_Like+ id,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result GetByIdLikes(Long id) {
        //获取用户id
        Set<String> range = stringRedisTemplate.opsForZSet().range(SystemConstants.is_Like + id, 0, 4);
        List<Long> ids = range.stream().map(s ->
                Long.valueOf(s)
        ).collect(Collectors.toList());

        List<UserDTO> list = new ArrayList<>();
       ids.forEach(
               iD -> {
                   User user = userService.getById(iD);
                   UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                   list.add(userDTO);
               }
       );
        //根据用户id查询对应用户，封装到List<UserDto>
        return Result.ok(list);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        save(blog);

        List<Follow> followUserId = followService.query().eq("follow_user_id", user.getId()).list();

        for (Follow follow:followUserId){
            Long userId = follow.getUserId();
            stringRedisTemplate.opsForZSet().add("feed:"+userId,blog.getId().toString(),System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryByBlogFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = "feed:"+userId;

        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        assert typedTuples != null;

        if(typedTuples == null || typedTuples.isEmpty()  ){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for( ZSetOperations.TypedTuple<String> t : typedTuples){
            ids.add(Long.valueOf(t.getValue()));
           long time = t.getScore().longValue();
            //遍历是往后的越来越小，所以，直接以 第一个数为最小值 和后面的数进行比较，
            // 一样就++，不一样就代替最小值并且重置次数
            //无论如何，便利到最后一点是最小值
            if(time == minTime){
                os++;
            }else{
               minTime = time;
                os = 1;
            }
        }

        String join = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + join + ")").list();
        for (Blog b : blogs){
            this.extracted(b);
            this.isBlogLike(b);
        }

        ScorllResult r = new ScorllResult();
        r.setList(blogs);
        r.setOffest(os);
        r.setMinTime(minTime);

        return Result.ok(r);
    }

    private void extracted(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    //显示是否有红心
    private void isBlogLike(Blog blog) {

        UserDTO user = UserHolder.getUser();
        if(user == null){
            blog.setIsLike(false);
        }
        else{
            Long id = blog.getId();
            Double score = stringRedisTemplate.opsForZSet().score(SystemConstants.is_Like + id, user.getId().toString());
        boolean member = true;

        if(score == null){
            member = false;
        }

        blog.setIsLike(BooleanUtil.isTrue(member));
    }
    }

}
