package com.inforefiner.custom.plugins.redis.config;

import com.inforefiner.custom.plugins.redis.container.RedisCommandsContainer;
import com.inforefiner.custom.plugins.redis.container.RedisCommandsContainerBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Protocol;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * Created by P0007 on 2020/3/30.
 */
@Slf4j
public class FlinkJedisConfigAdapter implements Serializable {

    private FlinkJedisPoolConfig jedisPoolConfig;

    private RedisCommandsContainer container;

    @Getter
    private String host;

    @Getter
    private int port;

    @Getter
    private int connectionTimeout;

    @Getter
    private String password;

    @Getter
    private int database;

    @Getter
    private int maxTotal;

    @Getter
    private int maxIdle;

    @Getter
    private int minIdle;

    @Getter
    private int maxRedirections;

    public FlinkJedisConfigAdapter(String host, int port, int connectionTimeout, String password, int database,
                                   int maxTotal, int maxIdle, int minIdle, int maxRedirections) {
       this.host = host;
       this.port = port;
       this.connectionTimeout = connectionTimeout;
       this.password = password;
       this.database = database;
       this.maxTotal = maxTotal;
       this.maxIdle = maxIdle;
       this.minIdle = minIdle;
       this.maxRedirections = maxRedirections;

        FlinkJedisPoolConfig jedisPoolConfig = initJedisPoolConfig(host, port, connectionTimeout, password, database, maxTotal, maxIdle, minIdle);
        RedisCommandsContainer container = RedisCommandsContainerBuilder.build(jedisPoolConfig);
        this.container = container;
        this.jedisPoolConfig = jedisPoolConfig;
    }

    private boolean isClusterEnabled() {
        return container.isClusterEnabled();
    }

    private Set<InetSocketAddress> getNodes() {
        return container.getNodes();
    }

    /**
     * 支持自动从单点配置转换到集群配置
     * @return
     */
    public FlinkJedisConfigBase getFlinkJedisConfig() {
        boolean clusterEnabled = isClusterEnabled();
        if (clusterEnabled) {
            Set<InetSocketAddress> nodes = getNodes();
            FlinkJedisClusterConfig jedisClusterConfig = initJedisClusterConfig(nodes);
            return jedisClusterConfig;
        } else {
            return jedisPoolConfig;
        }
    }

    /**
     * 初始化 jedis cluster connect pool
     * @param nodes
     * @return
     */
    private FlinkJedisClusterConfig initJedisClusterConfig(Set<InetSocketAddress> nodes) {
        return new FlinkJedisClusterConfig.Builder()
                        .setNodes(nodes)
                        .setTimeout(connectionTimeout)
                        .setMaxTotal(maxTotal)
                        .setMinIdle(minIdle)
                        .setMaxIdle(maxIdle)
                        .setMaxRedirections(maxRedirections)
                        .setPassword(password)
                        .build();
    }

    /**
     * 初始化 jedis单点connect pool
     * @param host
     * @param port
     * @param connectionTimeout
     * @param password
     * @param database
     * @param maxTotal
     * @param maxIdle
     * @param minIdle
     * @return
     */
    private FlinkJedisPoolConfig initJedisPoolConfig(String host, int port, int connectionTimeout, String password, int database, int maxTotal, int maxIdle, int minIdle) {
        return new FlinkJedisPoolConfig.Builder()
                .setHost(host)
                .setPort(port)
                .setPassword(password)
                .setMaxIdle(maxIdle)
                .setMinIdle(minIdle)
                .setDatabase(database)
                .setMaxTotal(maxTotal)
                .setTimeout(connectionTimeout)
                .build();
    }


    public static class Builder {
        private String host;
        private int port = Protocol.DEFAULT_PORT;
        private int timeout = Protocol.DEFAULT_TIMEOUT;
        private int database = Protocol.DEFAULT_DATABASE;
        private String password;
        private int maxTotal = GenericObjectPoolConfig.DEFAULT_MAX_TOTAL;
        private int maxIdle = GenericObjectPoolConfig.DEFAULT_MAX_IDLE;
        private int minIdle = GenericObjectPoolConfig.DEFAULT_MIN_IDLE;
        private int maxRedirections = 5;

        /**
         * Sets value for the {@code maxTotal} configuration attribute
         * for pools to be created with this configuration instance.
         *
         * @param maxTotal maxTotal the maximum number of objects that can be allocated by the pool, default value is 8
         * @return Builder itself
         */
        public Builder setMaxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
            return this;
        }

        /**
         * Sets value for the {@code maxIdle} configuration attribute
         * for pools to be created with this configuration instance.
         *
         * @param maxIdle the cap on the number of "idle" instances in the pool, default value is 8
         * @return Builder itself
         */
        public Builder setMaxIdle(int maxIdle) {
            this.maxIdle = maxIdle;
            return this;
        }

        /**
         * Sets value for the {@code minIdle} configuration attribute
         * for pools to be created with this configuration instance.
         *
         * @param minIdle the minimum number of idle objects to maintain in the pool, default value is 0
         * @return Builder itself
         */
        public Builder setMinIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        /**
         * Sets host.
         *
         * @param host host
         * @return Builder itself
         */
        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets port.
         *
         * @param port port, default value is 6379
         * @return Builder itself
         */
        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets timeout.
         *
         * @param timeout timeout, default value is 2000
         * @return Builder itself
         */
        public Builder setTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets database index.
         *
         * @param database database index, default value is 0
         * @return Builder itself
         */
        public Builder setDatabase(int database) {
            this.database = database;
            return this;
        }

        /**
         * Sets password.
         *
         * @param password password, if any
         * @return Builder itself
         */
        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets maxRedirections.
         *
         * @param maxRedirections
         * @return Builder itself
         */
        public Builder setMaxRedirections(int maxRedirections) {
            this.maxRedirections = maxRedirections;
            return this;
        }


        /**
         * Builds JedisPoolConfig.
         *
         * @return JedisPoolConfig
         */
        public FlinkJedisConfigAdapter build() {
            return new FlinkJedisConfigAdapter(host, port, timeout, password, database, maxTotal, maxIdle, minIdle, maxRedirections);
        }
    }


}
