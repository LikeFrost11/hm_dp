package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TTL;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Shop queryById(Long id) {
        String key = SHOP_KEY + id;
        String value = stringRedisTemplate.opsForValue().get(key);
        if(!StringUtil.isNullOrEmpty(value)){
            return JSONUtil.toBean(value, Shop.class);
        }
        Shop shop = getById(id);
        if(shop == null){
            throw new RuntimeException("店铺信息不存在");
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    @Override
    public void updateShopById(Shop shop) {
        updateById(shop);
        stringRedisTemplate.delete(SHOP_KEY + shop.getId());
    }
}
