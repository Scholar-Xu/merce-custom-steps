package com.inforefiner.custom.plugins.redis.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.util.SafeEncoder;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Redis command container if we want to connect to a single Redis server or to Redis sentinels
 * If want to connect to a single Redis server, please use the first constructor {@link #RedisContainer(JedisPool)}.
 * If want to connect to a Redis sentinels, please use the second constructor {@link #RedisContainer(JedisSentinelPool)}
 */
public class RedisContainer implements RedisCommandsContainer, Closeable {

    private static final Logger logger = LoggerFactory.getLogger(RedisContainer.class);

    private transient JedisPool jedisPool;
    private transient JedisSentinelPool jedisSentinelPool;

    /**
     * Use this constructor if to connect with single Redis server.
     *
     * @param jedisPool JedisPool which actually manages Jedis instances
     */
    public RedisContainer(JedisPool jedisPool) {
        Objects.requireNonNull(jedisPool, "Jedis Pool can not be null");
        this.jedisPool = jedisPool;
        this.jedisSentinelPool = null;
    }

    /**
     * Use this constructor if Redis environment is clustered with sentinels.
     *
     * @param sentinelPool SentinelPool which actually manages Jedis instances
     */
    public RedisContainer(final JedisSentinelPool sentinelPool) {
        Objects.requireNonNull(sentinelPool, "Jedis Sentinel Pool can not be null");
        this.jedisPool = null;
        this.jedisSentinelPool = sentinelPool;
    }

    /**
     * Closes the Jedis instances.
     */
    @Override
    public void close() throws IOException {
        if (this.jedisPool != null) {
            this.jedisPool.close();
        }
        if (this.jedisSentinelPool != null) {
            this.jedisSentinelPool.close();
        }
    }

    @Override
    public boolean isClusterEnabled() {
        Jedis jedis = null;
        try {
            jedis = getInstance();
            String[] info = jedis.info().split("\n");
            String version = Arrays.stream(info).filter(str -> str.contains("redis_version:")).toArray(String[]::new)[0];
            String clusterEnable = Arrays.stream(info).filter(str -> str.contains("cluster_enabled:")).toArray(String[]::new)[0];
            int mainVersion = Integer.parseInt(version.substring(14, version.indexOf(".")));
            boolean clusterEnabled = mainVersion > 2 && clusterEnable.length() > 0 && clusterEnable.contains("1");
            logger.info("Redis cluster enabled: {}", clusterEnabled);
            return clusterEnabled;
        } catch (Exception e) {
            logger.error("Get redis cluster info throw exception.", e);
        } finally {
            releaseInstance(jedis);
        }
        return false;
    }

    @Override
    public void open() throws Exception {

        // echo() tries to open a connection and echos back the
        // message passed as argument. Here we use it to monitor
        // if we can communicate with the cluster.

        getInstance().echo("Test");
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        Jedis jedis = null;
        try {
            jedis = getInstance();
            Map<String, String> value = jedis.hgetAll(key);
            return value;
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Cannot send Redis message with command HGET to key {} error message {}",
                        key, e.getMessage());
            }
            throw e;
        } finally {
            releaseInstance(jedis);
        }
    }

    @Override
    public List<Map<String, String>> hgetAllWithBatch(Set<String> keys) {
        Jedis jedis = null;
        try {
            jedis = getInstance();
            Pipeline pipelined = jedis.pipelined();
            for (String key : keys) {
                pipelined.hgetAll(key);
            }
            List<Object> objects = pipelined.syncAndReturnAll();
            List<Map<String, String>> valueMap = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                Object obj = objects.get(i);
                if (obj instanceof Map) {
                    Map<String, String> map = (Map<String, String>) obj;
                    valueMap.add(map);
                }
            }
            return valueMap;
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Cannot send Redis message with command HGETALL to key {} error message",
                        keys, e);
            }
            throw e;
        } finally {
            releaseInstance(jedis);
        }
    }

    @Override
    public Set<String> keys(String pattern) {
        Jedis jedis = null;
        try {
            jedis = getInstance();
            Set<String> keys = jedis.keys(pattern);
            return keys;
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Cannot send Redis message with command KEYS {} error message.",
                        pattern, e);
            }
            throw e;
        } finally {
            releaseInstance(jedis);
        }
    }

    @Override
    public void hset(final String key, final Map<String, String> value) {
        Jedis jedis = null;
        try {
            jedis = getInstance();
            jedis.hmset(key, value);
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Cannot send Redis message with command HMSET to key {} error message {}",
                        key, e.getMessage());
            }
            throw e;
        } finally {
            releaseInstance(jedis);
        }
    }

    @Override
    public void hsetex(String key, long expireTime, Map<String, String> value) {
        Jedis jedis = null;
        try {
            jedis = getInstance();
            Pipeline pipelined = jedis.pipelined();
            pipelined.hmset(key, value);
            pipelined.pexpire(key, expireTime);
            pipelined.sync();
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Cannot send Redis message with command HMSET to key {} error message.",
                        key, e);
            }
            throw e;
        } finally {
            releaseInstance(jedis);
        }
    }

    @Override
    public void set(final String key, final String value) {
        Jedis jedis = null;
        try {
            jedis = getInstance();
            jedis.set(key, value);
        } catch (Exception e) {
            if (logger.isErrorEnabled()) {
                logger.error("Cannot send Redis message with command SET to key {} error message {}",
                        key, e.getMessage());
            }
            throw e;
        } finally {
            releaseInstance(jedis);
        }
    }

    /**
     * Returns Jedis instance from the pool.
     *
     * @return the Jedis instance
     */
    private Jedis getInstance() {
        if (jedisSentinelPool != null) {
            return jedisSentinelPool.getResource();
        } else {
            return jedisPool.getResource();
        }
    }

    /**
     * Closes the jedis instance after finishing the command.
     *
     * @param jedis The jedis instance
     */
    private void releaseInstance(final Jedis jedis) {
        if (jedis == null) {
            return;
        }
        try {
            jedis.close();
        } catch (Exception e) {
            logger.error("Failed to close (return) instance to pool", e);
        }
    }

    @Override
    public Set<InetSocketAddress> getNodes() {
        Jedis jedis = null;
        Set<InetSocketAddress> nodes = new HashSet<>();
        try {
            jedis = getInstance();
            List<Object> clusterSlots = jedis.clusterSlots();
            for (Object slotInfoObj : clusterSlots) {
                List slotInfo = (List) slotInfoObj;
                Long sPos = (Long)slotInfo.get(0);
                Long ePos = (Long)slotInfo.get(1);

                List node = (List)slotInfo.get(3);
                String host = SafeEncoder.encode((byte[]) node.get(0));
                Long port = (Long)node.get(1);
                logger.info("{}:{} startSlot - endSlot: {} - {}", host, port, sPos, ePos);
                nodes.add(new InetSocketAddress(host, port.intValue()));
            }
        } catch (Exception e) {
            logger.error("Get redis cluster node throw exception.", e);
        } finally {
            releaseInstance(jedis);
        }
        return nodes;
    }
}
