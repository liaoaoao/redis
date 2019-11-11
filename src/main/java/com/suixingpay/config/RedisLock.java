package com.suixingpay.config;


import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
public class RedisLock {
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(RedisLock.class);

    private static final String LOCK_SIGN = "LOCK_SIGN_";

    // LUA脚本命令(原子性执行)
    public static final String UNLOCK_LUA;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("if redis.call(\"get\",KEYS[1]) == ARGV[1] ");
        sb.append("then ");
        sb.append("    return redis.call(\"del\",KEYS[1]) ");
        sb.append("else ");
        sb.append("    return 0 ");
        sb.append("end ");
        UNLOCK_LUA = sb.toString();
    }

    /**
     * 获取锁
     *
     * @param key 锁名
     * @param expire 超时时间(毫秒)
     * @return null(未获得锁)；not null(为key的value值，释放锁时使用)
     */
    public String setLock(String key, long expire) {
        try {
            String[] sign = new String[1];

            RedisCallback<String> callback = (connection) -> {
                JedisCommands commands = (JedisCommands) connection.getNativeConnection();
                sign[0] = LOCK_SIGN + UUID.randomUUID().toString();
                return commands.set(key, sign[0], "NX", "PX", expire);
            };
            String result = redisTemplate.execute(callback);
            if (StringUtils.isNotBlank(result)) {
                return sign[0];
            } else {
                return null;
            }
        } catch (Exception e) {
            logger.error("设置redis值异常:{}", e);
        }
        return null;
    }

    /**
     * 赋值
     *
     * @param key
     * @param expire 超时时间(毫秒)
     * @return 是否成功
     */
    public Boolean set(String key, String value, long expire) {
        try {
            String[] sign = new String[1];
            RedisCallback<String> callback = (connection) -> {
                JedisCommands commands = (JedisCommands) connection.getNativeConnection();
                sign[0] = value;
                return commands.set(key, sign[0], "NX", "PX", expire);
            };
            String result = redisTemplate.execute(callback);
            if (StringUtils.isNotBlank(result)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            logger.error("设置redis值异常:{}", e);
        }
        return false;
    }

    /**
     * 获取key值
     *
     * @param key
     * @return
     */
    public String get(String key) {
        try {
            RedisCallback<String> callback = (connection) -> {
                JedisCommands commands = (JedisCommands) connection.getNativeConnection();
                return commands.get(key);
            };
            String result = redisTemplate.execute(callback);
            return result;
        } catch (Exception e) {
            logger.error("获取redis的key值异常:{}", e);
        }
        return "";
    }

    /**
     * 释放锁
     *
     * @param key 锁名
     * @param sign 获取锁时获得的锁的value值
     * @return 是否成功
     */
    public boolean releaseLock(String key, String sign) {

        try {
            List<String> keys = new ArrayList<>();
            keys.add(key);
            List<String> args = new ArrayList<>();
            args.add(sign);

            // 使用lua脚本删除redis中匹配value的key
            // spring自带的执行脚本方法中，集群模式直接抛出不支持执行脚本的异常，所以只能拿到原redis的connection来执行脚本
            RedisCallback<Long> callback = (connection) -> {
                Object nativeConnection = connection.getNativeConnection();
                // 集群模式和单机模式虽然执行脚本的方法一样，但是没有共同的接口，所以只能分开执行
                // 集群模式
                if (nativeConnection instanceof JedisCluster) {
                    return (Long) ((JedisCluster) nativeConnection).eval(UNLOCK_LUA, keys, args);
                }
                // 单机模式
                else if (nativeConnection instanceof Jedis) {
                    return (Long) ((Jedis) nativeConnection).eval(UNLOCK_LUA, keys, args);
                }
                return 0L;
            };
            Long result = redisTemplate.execute(callback);
            return result != null && result > 0;
        } catch (Exception e) {
            logger.error("释放redis锁异常:{}", e);
        }
        return false;
    }
}
