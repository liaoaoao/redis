package com.suixingpay.service.impl;

import com.suixingpay.config.RedisLock;

import org.apache.commons.lang.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.suixingpay.service.TicketService;

@Slf4j
@Service
public class TicketServiceImpl implements TicketService {

    // 当前票剩余量
    private volatile int tickets = 10;

    // 锁名
    private static final String ROP_TICKET_LOCK = "tickets:lock";
    // 锁过期时间 30s
    private static final Long ROP_TICKET_LOCK_TIME_OUT = 30000L;
    // 获取锁超时时间 10s
    private static final Long ROP_TICKET_LOCK_GET_TIME_OUT = 10000L;
    // 消息存放key
    private static final String ROP_TICKET_MESSAGE = "ticket:buy:message";

    @Autowired
    private RedisLock redisLock;

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;


    @Override
    public Boolean sellTickets(String userName) {
        if (log.isInfoEnabled()) {
            log.info("用户【{}】开始抢票！", userName);
        }
        if (tickets <= 0) {
            log.info("票已售罄！");
            return false;
        }
        //获取锁
        String lockSign = redisLock.setLock(ROP_TICKET_LOCK, ROP_TICKET_LOCK_TIME_OUT);
        Long oldTimeStamp = System.currentTimeMillis();
        while (true) {
            // 不为空则获取到锁
            if (StringUtils.isNotBlank(lockSign)) {
                if (tickets <= 0) {
                    log.info("票已售罄！");
                    return false;
                }
                log.info("用户【{}】获取到锁", userName);
                String content = "用户【" + userName + "】购买到票！票量剩余：【" + tickets + "】张";
                // 消息添加到列表中
                Long result =
                        redisTemplate.opsForList().leftPush(ROP_TICKET_MESSAGE, content);
                if (null != result && result > 0) {
                    tickets--;
                    log.info("用户【{}】抢票成功！票量剩余：【{}】张", new Object[]{userName, tickets});
                }
                //释放锁
                redisLock.releaseLock(ROP_TICKET_LOCK, lockSign);
                return true;
            }
            Long nowTimeStamp = System.currentTimeMillis();
            // 操作是否超时
            boolean workContinue = (nowTimeStamp - oldTimeStamp) > ROP_TICKET_LOCK_GET_TIME_OUT;
            if (workContinue) {
                log.error("用户【{}】操作超时", userName);
                break;
            }
        }
        return false;
    }

    @Override
    public Object showCurrentProgress() {
        return redisTemplate.opsForList().range(ROP_TICKET_MESSAGE, 0, -1);
    }
}
