package com.inforefiner.custom.plugins.redis.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * Redis command container if we want to connect to a Redis cluster.
 */
public class RedisClusterContainer implements RedisCommandsContainer, Closeable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(RedisClusterContainer.class);

    private transient JedisCluster jedisCluster;

    /**
     * Initialize Redis command container for Redis cluster.
     *
     * @param jedisCluster JedisCluster instance
     */
    public RedisClusterContainer(JedisCluster jedisCluster) {
        Objects.requireNonNull(jedisCluster, "Jedis cluster can not be null");

        this.jedisCluster = jedisCluster;
    }

    @Override
    public void open() throws Exception {

        // echo() tries to open a connection and echos back the
        // message passed as argument. Here we use it to monitor
        // if we can communicate with the cluster.

        jedisCluster.echo("Test");
    }

    @Override
    public Map<String, String> hgetAll(String key) {
        try {
            Map<String, String> values = jedisCluster.hgetAll(key);
            return values;
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Cannot send Redis message with command SET to key {} error message {}",
                        key, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public List<Map<String, String>> hgetAllWithBatch(Set<String> keys) {
        try {
            List<Map<String, String>> valueMap = new ArrayList<>();
            for (String key : keys) {
                Map<String, String> values = jedisCluster.hgetAll(key);
                valueMap.add(values);
            }
            return valueMap;
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Cannot send Redis message with command HGETALL to key {} error message",
                        keys, e);
            }
            throw e;
        }
    }

    @Override
    public Set<String> keys(String pattern) {
        try {
            Set<String> keys = jedisCluster.keys(pattern);
            return keys;
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Cannot send Redis message with command keys {} error message {}",
                        pattern, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public void hset(final String key, final Map<String, String> value) {
        try {
            jedisCluster.hmset(key, value);
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Cannot send Redis message with command HSET to hash {} error message {}",
                        key, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public void hsetex(String key, long expireTime, Map<String, String> value) {
        try {
            jedisCluster.hmset(key, value);
            jedisCluster.pexpire(key, expireTime);
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Cannot send Redis message with command HSET to hash {} error message {}",
                        key, e.getMessage());
            }
            throw e;
        }
    }

    @Override
    public void set(final String key, final String value) {
        try {
            jedisCluster.set(key, value);
        } catch (Exception e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Cannot send Redis message with command SET to key {} error message {}",
                    key, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Closes the {@link JedisCluster}.
     */
    @Override
    public void close() throws IOException {
        this.jedisCluster.close();
    }

    @Override
    public boolean isClusterEnabled() {
        return true;
    }

    @Override
    public Set<InetSocketAddress> getNodes() {
        Set<InetSocketAddress> nodes = new HashSet<>();
        Map<String, JedisPool> clusterNodes = jedisCluster.getClusterNodes();
        Set<String> urls = clusterNodes.keySet();
        for (String url : urls) {
            String[] hostAndPort = url.split(":");
            String host = hostAndPort[0];
            int port = Integer.parseInt(hostAndPort[1]);
            InetSocketAddress node = new InetSocketAddress(host, port);
            nodes.add(node);
        }
        return nodes;
    }
}
