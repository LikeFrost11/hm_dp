package com.hmdp.utils;

public class RedisConstants {
    public static final String CODE_KEY = "login:code:";
    public static final Long CODE_TTL = 5l;
    public static final String TOKEN_KEY = "login:token:";
    public static final Long TOKEN_TTL = 30l;
    public static final String SHOP_KEY = "cache:shop:";
    public static final Long SHOP_TTL = 30l;
    public static final Long NULL_TTL = 30l;
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_TTL = 10l;
    public static final String BLOG_LIKE_KEY = "blog:like:";

}
