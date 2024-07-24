package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class RedisCacheUtils {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public RedisCacheUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object o, Long expireTime, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(o), expireTime, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object o, Long expireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(o);
        //设置逻辑过期
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R getWithLogicalExpire(String prefix, ID id, Class<R> clazz, Long time, TimeUnit unit,
                                          Function<ID, R> dbFallback) {
        String key = prefix + id;
        //查询缓存
        String s = stringRedisTemplate.opsForValue().get(key);
        //未命中，返回空值
        if(StringUtil.isNullOrEmpty(s)){
            return null;
        }
        //判断是否过期
        RedisData redisData = JSONUtil.toBean(s, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期直接返回
            return r;
        }
        //过期，尝试获取锁重建缓存
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if(tryLock(lockKey)){
            //获取成功，开新线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        //获取失败，直接返回过期数据
        return r;
    }

    public <R, ID> R getWithCacheNull(String prefix, ID id, Class<R> clazz, Long time, TimeUnit unit,
                                      Function<ID, R> dbFallback) {
        String key = prefix + id;
        //查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //命中且不为空值，直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, clazz);
        }
        //命中了空值
        if(json != null){//经过上面的判断json只可能是空串或者null
            return null;
        }
        //未命中，查询数据库
        R data = dbFallback.apply(id);
        //数据不存在，缓存空值
        if(data == null){
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据存在，存入缓存，返回数据
        set(key, data, time, unit);
        return data;


    }

    private boolean tryLock(String key) {
        //利用setIfAbsent()的机制模拟获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
