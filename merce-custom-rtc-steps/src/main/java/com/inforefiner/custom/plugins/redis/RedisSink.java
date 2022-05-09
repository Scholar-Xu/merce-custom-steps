package com.inforefiner.custom.plugins.redis;

import com.inforefiner.custom.plugins.redis.config.FlinkJedisConfigBase;
import com.inforefiner.custom.plugins.redis.container.RedisCommandsContainer;
import com.inforefiner.custom.plugins.redis.container.RedisCommandsContainerBuilder;
import com.inforefiner.custom.plugins.redis.mapper.RedisCommand;
import com.inforefiner.custom.plugins.redis.mapper.RedisCommandDescription;
import com.inforefiner.custom.plugins.redis.mapper.RedisMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class RedisSink<IN> extends RichSinkFunction<IN> {

    private static final Logger logger = LoggerFactory.getLogger(RedisSink.class);

    private String additionalKey;
    private RedisMapper<IN> redisSinkMapper;
    private RedisCommand redisCommand;

    private FlinkJedisConfigBase flinkJedisConfigBase;
    private RedisCommandsContainer redisCommandsContainer;

    public RedisSink(FlinkJedisConfigBase flinkJedisConfigBase, RedisMapper<IN> redisSinkMapper) {
        Objects.requireNonNull(flinkJedisConfigBase, "Redis connection pool config should not be null");
        Objects.requireNonNull(redisSinkMapper, "Redis Mapper can not be null");
        Objects.requireNonNull(redisSinkMapper.getCommandDescription(), "Redis Mapper data type description can not be null");

        this.flinkJedisConfigBase = flinkJedisConfigBase;

        this.redisSinkMapper = redisSinkMapper;
        RedisCommandDescription redisCommandDescription = redisSinkMapper.getCommandDescription();
        this.redisCommand = redisCommandDescription.getCommand();
        this.additionalKey = redisCommandDescription.getAdditionalKey();
    }

    /**
     * Initializes the connection to Redis by either cluster or sentinels or single server.
     *
     * @throws IllegalArgumentException if jedisPoolConfig, jedisClusterConfig and jedisSentinelConfig are all null
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        try {
            this.redisCommandsContainer = RedisCommandsContainerBuilder.build(this.flinkJedisConfigBase);
            this.redisCommandsContainer.open();
        } catch (Exception e) {
            logger.error("Redis has not been properly initialized: ", e);
            throw e;
        }
    }

    @Override
    public void invoke(IN input, Context context) throws Exception {
        String key = redisSinkMapper.getKeyFromData(input);
        long expireTime = redisSinkMapper.getExpireTime();
        logger.debug("expireTime {},input row {} ", expireTime, input);
        Map<String, String> valueMap = redisSinkMapper.getValueFromData(input);
        switch (redisCommand) {
            case HMSET:
                if (expireTime != 0) {
                    this.redisCommandsContainer.hsetex(key, expireTime, valueMap);
                } else {
                    this.redisCommandsContainer.hset(key, valueMap);
                }
                break;
            default:
                throw new IllegalArgumentException("Cannot process such data type: " + redisCommand);
        }
    }

    /**
     * Closes commands container.
     * @throws IOException if command container is unable to close.
     */
    @Override
    public void close() throws IOException {
        if (redisCommandsContainer != null) {
            redisCommandsContainer.close();
        }
    }
}
