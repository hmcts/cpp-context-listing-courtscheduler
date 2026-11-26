package uk.gov.moj.cpp.courtscheduler.cache;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {
    private static final String HOST = "host";
    private static final String KEY1 = "key1";
    private static final String REDIS_CLIENT = "redisClient";
    private static final String VALUE1 = "value1";

    @InjectMocks
    private RedisCacheService redisCacheService;

    @Mock
    private RedisClient redisClient;

    @Mock
    private StatefulRedisConnection statefulRedisConnection;

    @Mock
    private RedisCommands<String, String> redisCommands;

    @BeforeEach
    public void setUp() {
        redisCacheService = new RedisCacheService();
        mockRedis();
    }

    @Test
    void shouldAddToCacheSuccessfully() {
        when(redisClient.connect()).thenReturn(statefulRedisConnection);
        when(statefulRedisConnection.sync()).thenReturn(redisCommands);
        // given
        when(redisCommands.set(eq(KEY1), eq(VALUE1), any())).thenReturn(VALUE1);

        // when
        final String result = redisCacheService.add(KEY1, VALUE1);

        // then
        verify(redisCommands).set(eq(KEY1), eq(VALUE1), any(SetArgs.class));
        assertThat(result, is(VALUE1));
    }

    @Test
    void shouldNotAddToCacheWhenHostIsLocalHost() {
        //given
        setField(redisCacheService, HOST, "localhost");
        // when
        final String result = redisCacheService.add(KEY1, VALUE1);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldAddToCacheSuccessfullyWhenRedisClientIsNullBySettingUpRedisClient() {
        setField(redisCacheService, REDIS_CLIENT, null);

        // when
        final String result = redisCacheService.add(KEY1, VALUE1);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldGetFromCacheSuccessfully() {
        when(redisClient.connect()).thenReturn(statefulRedisConnection);
        when(statefulRedisConnection.sync()).thenReturn(redisCommands);
        // given
        when(redisCommands.get(KEY1)).thenReturn(VALUE1);

        // when
        final String result = redisCacheService.get(KEY1);

        // then
        assertThat(result, is(VALUE1));
    }

    @Test
    void shouldNotGetFromCacheWhenHostIsLocalHost() {
        setField(redisCacheService, HOST, "localhost");

        // when
        final String value = redisCacheService.get(KEY1);

        // then
        assertThat(value, is(nullValue()));
    }

    @Test
    void shouldNotGetFromCacheWhenRedisClientIsNull() {
        setField(redisCacheService, REDIS_CLIENT, null);

        // when
        final String value = redisCacheService.get(KEY1);

        // then
        assertThat(value, is(nullValue()));
    }

    @Test
    void shouldRemoveFromCacheSuccessfully() {
        when(redisClient.connect()).thenReturn(statefulRedisConnection);
        when(statefulRedisConnection.sync()).thenReturn(redisCommands);
        assertThat(redisCacheService.remove(KEY1), is(true));

        verify(redisCommands, times(1)).del(KEY1);
    }

    @Test
    void shouldReturnFalseIfRedisConnectionExceptionOccurredWhilstRemovingFromCache() {
        when(redisClient.connect()).thenThrow(RedisConnectionException.class);
        assertThat(redisCacheService.remove(KEY1), is(false));

        verify(redisCommands, never()).del(KEY1);
    }

    @Test
    void shouldFlushAllCacheKeys() {
        when(redisClient.connect()).thenReturn(statefulRedisConnection);
        when(statefulRedisConnection.sync()).thenReturn(redisCommands);
        // when
        redisCacheService.flushAllCacheKeys();

        // then
        assertThat(redisCacheService.get(KEY1), is(nullValue()));
    }

    @Test
    void shouldNotFlushAllCacheKeysWhenHostIsLocalHost() {
        setField(redisCacheService, HOST, "localhost");

        // when
        final String result = redisCacheService.flushAllCacheKeys();

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldNotFlushAllCacheKeysWhenRedisClientIsNull() {
        setField(redisCacheService, REDIS_CLIENT, null);

        // when
        final String result = redisCacheService.flushAllCacheKeys();

        // then
        assertThat(result, is(nullValue()));
    }

    private void mockRedis() {
        setField(redisCacheService, REDIS_CLIENT, redisClient);
        setField(redisCacheService, HOST, "redisHost");
        setField(redisCacheService, "key", "test_key");
        setField(redisCacheService, "port", "6380");
        setField(redisCacheService, "useSsl", "false");
        setField(redisCacheService, "ttlSeconds", "86400");


    }

}