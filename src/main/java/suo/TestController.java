package suo;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;


@RestController
public class TestController {

    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/test1")
    public Object test1() {
        return redisTemplate.opsForValue().get("product-count");
    }

    @GetMapping("/test2")
    public String test2() {
        synchronized (TestController.class) {
            Integer productCount = Integer.parseInt(redisTemplate.opsForValue().get("product-count").toString());
            if (productCount <= 0) {
                System.out.println("库存不足");
                return "fail";
            }
            System.out.println("成功购买，库存：" + productCount);
            redisTemplate.opsForValue().set("product-count", String.valueOf(productCount - 1));
        }
        return "success";
    }

    @GetMapping("/test3")
    public String test3() {
        String concurrentKey = "product";
        // 获取锁
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(concurrentKey, "test", 5, TimeUnit.SECONDS); // 此处设置超时时间，setIfAbsent是原子操作
        if (!flag) { // flag=false说明concurrentKey已经存在了，获取锁失败
            return "fail";
        }
        try {
            // 业务代码
            Integer productCount = Integer.parseInt(redisTemplate.opsForValue().get("product-count").toString());
            if (productCount <= 0) {
                System.out.println("库存不足");
                return "fail";
            }
            productCount = productCount - 1;
            System.out.println("成功购买，库存：" + productCount);
            redisTemplate.opsForValue().set("product-count", String.valueOf(productCount));
        } finally {
            // 释放锁，放在finally中是为了防止业务代码抛异常，从而导致这行代码不被执行，即锁永远不被释放，之后的线程都不能获取锁。
            redisTemplate.delete(concurrentKey);
        }

        return "success";
    }

    @GetMapping("/test5")
    public String test5() {
        String concurrentKey = "product";

        String concurrentValue = UUID.randomUUID().toString();
        // 获取锁
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(concurrentKey, concurrentValue, 10, TimeUnit.SECONDS); // 等价于setnx key value
        if (!flag) { // flag=false说明concurrentKey已经存在了，获取锁失败
            return "fail";
        }
        try {
            // 业务代码
            Integer productCount = Integer.parseInt(redisTemplate.opsForValue().get("product-count").toString());
            if (productCount <= 0) {
                System.out.println("库存不足");
                return "fail";
            }
            productCount = productCount - 1;
            System.out.println("成功购买，库存：" + productCount);
            redisTemplate.opsForValue().set("product-count", String.valueOf(productCount));
        } finally {
            // 判断是否是自己加的锁，如果是可以释放
            if (concurrentValue.equals(redisTemplate.opsForValue().get(concurrentKey))) {
                // 释放锁，放在finally中是为了防止业务代码抛异常，从而导致这行代码不被执行，即锁永远不被释放，之后的线程都不能获取锁。
                redisTemplate.delete(concurrentKey);
            }
        }

        return "success";
    }

    @Autowired
    private Redisson redisson;

    @GetMapping("/test6")
    public String test6() {
        String concurrentKey = "product";

        RLock redissonLock = redisson.getLock(concurrentKey);
        // 获取锁
        redissonLock.lock(30, TimeUnit.SECONDS);
        try {
            // 业务代码
            Integer productCount = Integer.parseInt(redisTemplate.opsForValue().get("product-count").toString());
            if (productCount <= 0) {
                System.out.println("库存不足");
                return "fail";
            }
            productCount = productCount - 1;
            System.out.println("成功购买，库存：" + productCount);
            redisTemplate.opsForValue().set("product-count", String.valueOf(productCount));
        } finally {
            // 释放锁
            redissonLock.unlock();
        }
        return "success";
    }
}


