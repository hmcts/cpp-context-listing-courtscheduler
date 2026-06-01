package uk.gov.moj.cpp.courtscheduler.cache;

import static java.util.Objects.isNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrated from {@code @Stateless} EJB + Justice Services {@code @Value} key/defaultValue
 * configuration to a Spring {@code @Service} with {@code @Value("${...:default}")}
 * placeholders bound to the {@code redis.common-cache.*} block in {@code application.yaml}.
 */
@Service
public class RedisCacheService implements CacheService{
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCacheService.class);

    private static final String LOCALHOST = "localhost";
    private static final Duration CONNECT_TIMEOUT_ONE_SEC = Duration.ofSeconds(3);
    private static final String DB_NAME = "0";

    @Value("${redis.common-cache.host:localhost}")
    private String host;

    @Value("${redis.common-cache.key-prefix:none}")
    private String key;

    @Value("${redis.common-cache.port:6380}")
    private String port;

    @Value("${redis.common-cache.use-ssl:false}")
    private String useSsl;

    @Value("${redis.common-cache.key-ttl-seconds:86400}")
    private String ttlSeconds;

    private RedisClient redisClient = null;

    @Override
    public String add(final String key, final String value) {
        if (LOCALHOST.equals(host)) {
            return null;
        }
        initializeRedisClientIfNot("add");
        return executeAddCommand(key, value, Integer.parseInt(ttlSeconds));
    }

    @Override
    public String add(final String key, final String value, final Integer timeToLive) {
        if (LOCALHOST.equals(host)) {
            return null;
        }
        initializeRedisClientIfNot("add");
        return executeAddCommand(key, value, timeToLive);
    }

    @Override
    public String get(final String key) {
        if (LOCALHOST.equals(host)) {
            return null;
        }
        initializeRedisClientIfNot("get");
        return executeGetCommand(key);
    }

    @Override
    public boolean remove(final String key) {
        if (LOCALHOST.equals(host)) {
            return true;
        }
        initializeRedisClientIfNot("remove");
        return executeRemoveCommand(key);
    }

    @Override
    public String flushAllCacheKeys() {
        if (LOCALHOST.equals(host)) {
            return null;
        }
        initializeRedisClientIfNot("flushAllCacheKeys");
        return executeFlushAllCommand();
    }

    private void initializeRedisClientIfNot(final String methodName) {
        if (isNull(redisClient)) {
            LOGGER.info("Inside CacheService.{}() - redisClient not initialised; invoking setRedisClient()", methodName);
            setRedisClient();
        }
    }

    private String executeAddCommand(final String key, final String value, final Integer timeToLive) {
        try (final StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            final RedisCommands<String, String> command = connection.sync();
            final SetArgs args = new SetArgs().ex(timeToLive);
            return command.set(key, value, args);
        } catch (final RedisConnectionException redisConnectionException) {
            LOGGER.warn("Exception in RedisCache executeAddCommand() - {}", redisConnectionException.getMessage(), redisConnectionException);
            return null;
        }
    }

    @SuppressWarnings({"squid:S2221"})
    private String executeGetCommand(final String key) {
        try (final StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            final RedisCommands<String, String> command = connection.sync();
            return command.get(key);
        } catch (final RedisConnectionException redisConnectionException) {
            LOGGER.warn("Exception in RedisCache executeGetCommand() - {} ", redisConnectionException.getMessage(), redisConnectionException);
            return null;
        }
    }

    private boolean executeRemoveCommand(final String key) {
        try (final StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            final RedisCommands<String, String> command = connection.sync();
            LOGGER.info("Cache DEL:Key:{}", key);
            command.del(key);
            return true;
        } catch (final RedisConnectionException redisConnectionException) {
            LOGGER.warn("Exception in RedisCache executeRemoveCommand() - {} ", redisConnectionException.getMessage(), redisConnectionException);
            return false;
        }
    }

    private String executeFlushAllCommand() {
        try (final StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            final RedisCommands<String, String> command = connection.sync();
            return command.flushdb();
        } catch (final RedisConnectionException redisConnectionException) {
            LOGGER.warn("Exception in RedisCache executeFlushAllCommand() - {}", redisConnectionException.getMessage(), redisConnectionException);
            return null;
        }
    }

    private void setRedisClient() {
        LOGGER.info("Redis host : {}", host);
        final String keyPart = ("none".equals(this.key) ? "" : this.key + "@");
        final RedisURI redisURI = RedisURI.create("redis://" + keyPart + host + ":" + port + "/" + DB_NAME);
        redisURI.setSsl(Boolean.parseBoolean(useSsl));

        redisClient = RedisClient.create(redisURI);

        final SocketOptions socketOptions = SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT_ONE_SEC).build();

        final ClientOptions clientOptions = ClientOptions.builder().socketOptions(socketOptions).build();
        this.redisClient.setOptions(clientOptions);
    }
}
