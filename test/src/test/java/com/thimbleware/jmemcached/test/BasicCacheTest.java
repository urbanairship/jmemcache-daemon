package com.thimbleware.jmemcached.test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.thimbleware.jmemcached.Cache;
import com.thimbleware.jmemcached.CacheElement;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;

/**
 */
@RunWith(Parameterized.class)
public class BasicCacheTest extends AbstractCacheTest {

    public static final int NO_EXPIRE = 0;

    public BasicCacheTest(CacheType cacheType, int blockSize, ProtocolMode protocolMode) {
        super(cacheType, blockSize, protocolMode);
    }

    @Test
    public void testPresence() {
        assertNotNull(cache);
        assertEquals("initial cache is empty", 0, cache.getCurrentItems());
        assertEquals("initialize maximum size matches max bytes", MAX_BYTES, cache.getLimitMaxBytes());
        assertEquals("initialize size is empty", 0, cache.getCurrentBytes());
    }

    @Test
    public void testAddGet() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));
        String testvalue = "87654321";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache
        assertEquals(cache.add(element), Cache.StoreResponse.STORED);

        // get result
        CacheElement result = cache.get(testKey)[0];

        // must be non null and match the original
        assertNotNull("got result", result);

        // assert no miss
        assertEquals("misses", 0, cache.getGetMisses());

        assertEquals("data length matches", result.size(), element.size());
        assertEquals("data matches", element.getData(), result.getData());
        assertEquals("key matches", element.getKey(), result.getKey());

        assertEquals("cache has 1 element", 1, cache.getCurrentItems());
    }

    @Test
    public void testAddReplace() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));

        String testvalue = "87654321";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache
        assertEquals(Cache.StoreResponse.STORED, cache.add(element));

        // get result
        CacheElement result = cache.get(testKey)[0];

        // assert no miss
        assertEquals("confirmed no misses", 0, cache.getGetMisses());

        // must be non null and match the original
        assertNotNull("got result", result);
        assertEquals("data length matches", result.size(), element.size());
        assertEquals("data matches", element.getData(), result.getData());

        assertEquals("cache has 1 element", 1, cache.getCurrentItems());

        // now replace
        testvalue = "54321";
        element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache
        assertEquals(cache.replace(element), Cache.StoreResponse.STORED);

        // get result
        result = cache.get(testKey)[0];

        // assert no miss
        assertEquals("confirmed no misses", 0, cache.getGetMisses());

        // must be non null and match the original
        assertNotNull("got result", result);
        assertEquals("data length matches", result.size(), element.size());
        assertEquals("data matches", element.getData(), result.getData());
        assertEquals("key matches", result.getKey(), element.getKey());
        assertEquals("cache has 1 element", 1, cache.getCurrentItems());

    }

    @Test
    public void testReplaceFail() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));

        String testvalue = "87654321";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache
        assertEquals(cache.replace(element), Cache.StoreResponse.NOT_STORED);

        // get result
        CacheElement result = cache.get(testKey)[0];

        // assert miss
        assertEquals("confirmed no misses", 1, cache.getGetMisses());

        assertEquals("cache is empty", 0, cache.getCurrentItems());
        assertEquals("maximum size matches max bytes", MAX_BYTES, cache.getLimitMaxBytes());
        assertEquals("size is empty", 0, cache.getCurrentBytes());
    }

    @Test
    public void testSet() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));

        String testvalue = "87654321";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache
        assertEquals(cache.set(element), Cache.StoreResponse.STORED);

        // get result
        CacheElement result = cache.get(testKey)[0];

        // assert no miss
        assertEquals("confirmed no misses", 0, cache.getGetMisses());

        // must be non null and match the original
        assertNotNull("got result", result);
        assertEquals("data length matches", result.size(), element.size());
        assertEquals("data matches", element.getData(), result.getData());
        assertEquals("key matches", result.getKey(), element.getKey());

        assertEquals("cache has 1 element", 1, cache.getCurrentItems());
    }

    @Test
    public void testAddAddFail() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));

        String testvalue = "87654321";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache
        assertEquals(Cache.StoreResponse.STORED, cache.add(element));

        // put in cache again and fail
        assertEquals(Cache.StoreResponse.EXISTS, cache.add(element));

        assertEquals("cache has only 1 element", 1, cache.getCurrentItems());
    }

    @Test
    public void testAddFlush() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));

        String testvalue = "87654321";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache, then flush
        cache.add(element);

        cache.flush_all();

        assertEquals("size of cache matches is empty after flush", 0, cache.getCurrentBytes());
        assertEquals("cache has no elements after flush", 0, cache.getCurrentItems());
    }

    @Test
    public void testSetAndIncrement() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));

        String testvalue = "1";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        
        long nowSec = System.currentTimeMillis() / 1000;
        int expireSec = (int)nowSec + 5000; // expire 5000 seconds from now
        
        // put in cache
        assertEquals(cache.set(element), Cache.StoreResponse.STORED);

        // cache expiry doesn't actually renew in real memcached
        
        // increment
        assertEquals("value correctly incremented", Long.valueOf(2), cache.get_add(testKey, 1, 0, expireSec));
        assertEquals("expiration should be ignored on an update", NO_EXPIRE, cache.get(testKey)[0].getExpire());
        
        // increment
        assertEquals("value correctly incremented", Long.valueOf(3), cache.get_add(testKey, 1, 0, NO_EXPIRE));

        // increment by more
        assertEquals("value correctly incremented", Long.valueOf(7), cache.get_add(testKey, 4, 0, NO_EXPIRE));
        
        // decrement
        assertEquals("value correctly decremented", Long.valueOf(2), cache.get_add(testKey, -5, 0, NO_EXPIRE));
        
        Key testKey2 = new Key(ChannelBuffers.wrappedBuffer("345678".getBytes()));
        assertEquals("default value stored", Long.valueOf(50), cache.get_add(testKey2, 0, 50, expireSec));
        assertEquals("expiration correctly set", expireSec, cache.get(testKey2)[0].getExpire());
    }


    @Test
    public void testSetAndAppendPrepend() {
        Key testKey = new Key(ChannelBuffers.wrappedBuffer("12345678".getBytes()));

        String testvalue = "1";

        LocalCacheElement element = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        element.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));

        // put in cache
        assertEquals(cache.set(element), Cache.StoreResponse.STORED);

        // increment
        LocalCacheElement appendEl = new LocalCacheElement(testKey, 0, NO_EXPIRE, 0L);
        appendEl.setData(ChannelBuffers.wrappedBuffer(testvalue.getBytes()));
        Cache.StoreResponse append = cache.append(appendEl);
        assertEquals("correct response", append, Cache.StoreResponse.STORED);
        LocalCacheElement[] elements = cache.get(testKey);
        assertEquals("right # elements", 1, elements.length);
        ChannelBuffer data = elements[0].getData();
        assertEquals(ChannelBuffers.wrappedBuffer("11".getBytes()), data);
    }

}
