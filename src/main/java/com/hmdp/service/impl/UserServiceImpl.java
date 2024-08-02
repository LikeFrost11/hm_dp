package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    @Resource
    private UserMapper userMapper;

    /*
    发送短信验证码
     */
    @Override
    public Result sendCode(String phone) {
        //1.验证手机号是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确！");
        }
        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到redis
        stringRedisTemplate.opsForValue().set(CODE_KEY + phone, code, CODE_TTL, TimeUnit.MINUTES);
        //4.发送验证码（模拟）
        log.info("验证码为: {}", code);
        return Result.ok();
    }

    /*
    实现验证码登录
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        //1.验证手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式不正确！");
        }
        //验证code
        String code = stringRedisTemplate.opsForValue().get(CODE_KEY + loginForm.getPhone());
        if (!loginForm.getCode().equals(code)) {
            return Result.fail("验证码不正确！");
        }
        //查询用户
        User user = userMapper.findByPhone(loginForm.getPhone());
        //保存用户
        if (user == null) {
            user = new User();
            user.setPhone(loginForm.getPhone());
            user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            userMapper.insert(user);
        }
        //生成随机token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        //保存到redis
        String tokenKey = TOKEN_KEY + token;
        userMap.put("id", userDTO.getId() + "");
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 设置token有效期
        stringRedisTemplate.expire(tokenKey, TOKEN_TTL, TimeUnit.MINUTES);

        // 返回token
        return Result.ok(token);


    }
}
