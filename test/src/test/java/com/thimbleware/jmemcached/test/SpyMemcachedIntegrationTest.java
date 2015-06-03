package com.thimbleware.jmemcached.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.MemcachedClient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration test using Spymemcached 2.3
 * TODO flush
 * TODO shutdown
 * TODO delete
 * TODO more tests
 */
@RunWith(Parameterized.class)
public class SpyMemcachedIntegrationTest extends AbstractCacheTest {


    private MemcachedClient _client;
    private InetSocketAddress address;

    protected static final String KEY = "MyKey";

    protected static final int TWO_WEEKS = 1209600; // 60*60*24*14 = 1209600 seconds in 2 weeks.

    public SpyMemcachedIntegrationTest(CacheType cacheType, int blockSize, ProtocolMode protocolMode) {
        super(cacheType, blockSize, protocolMode);
    }

    @Before
    public void setUp() throws Exception {
        super.setup();

        this.address = new InetSocketAddress("localhost", getPort());
        if (getProtocolMode() == ProtocolMode.BINARY)
            _client = new MemcachedClient( new BinaryConnectionFactoryWithTimeout(60000), Arrays.asList( address ) );
        else
            _client = new MemcachedClient( Arrays.asList( address ) );
    }
    
    private class BinaryConnectionFactoryWithTimeout extends BinaryConnectionFactory {
	
	private long timeoutMs;
	
	public BinaryConnectionFactoryWithTimeout(long timeoutMs){
	    super();
	    this.timeoutMs = timeoutMs;
	}
	
	@Override
	public long getOperationTimeout(){
	    return timeoutMs;
	}
    }

    @After
    public void tearDown() throws Exception {
        if (_client != null)
            _client.shutdown();
    }


    @Test
    public void testGetSet() throws IOException, InterruptedException, ExecutionException {
        Future<Boolean> future = _client.set("foo", 5000, "bar");
        assertTrue(future.get());
        assertEquals( "bar", _client.get( "foo" ) );
    }
    
    @Test
    public void testIncrDecr() throws ExecutionException, InterruptedException {
        Future<Boolean> future = _client.set("foo", 0, "1");
        assertTrue(future.get());
        assertEquals( "1", _client.get( "foo" ) );
        _client.incr( "foo", 5 );
        assertEquals( "6", _client.get( "foo" ) );
        _client.decr( "foo", 10 );
        assertEquals( "0", _client.get( "foo" ) );
        
        assertEquals( null, _client.get( "bar" ) );
        long retVal = _client.incr( "bar", 5 );
        assertEquals( -1, retVal);
    }

    @Test
    public void testPresence() {
        assertEquals("initial cache is empty", 0, getDaemon().getCache().getCurrentItems());
        assertEquals("initialize size is empty", 0, getDaemon().getCache().getCurrentBytes());
    }

    @Test
    public void testStats() throws ExecutionException, InterruptedException {
        Map<SocketAddress, Map<String, String>> stats = _client.getStats();
        Map<String, String> statsMap = stats.get(address);
        assertNotNull(statsMap);
        assertEquals("0", statsMap.get("cmd_gets"));
        assertEquals("0", statsMap.get("cmd_sets"));
        Future<Boolean> future = _client.set("foo", 86400, "bar");
        assertTrue(future.get());
        _client.get("foo");
        _client.get("bug");
        stats = _client.getStats();
        statsMap = stats.get(address);
        assertEquals("2", statsMap.get("cmd_gets"));
        assertEquals("1", statsMap.get("cmd_sets"));
    }

    @Test
    public void testBinaryCompressed() throws ExecutionException, InterruptedException {
        Future<Boolean> future = _client.add("foo", 86400, "foobarshoe");
        assertEquals(true, future.get());
        assertEquals("wrong value returned from cache", "foobarshoe",  _client.get("foo"));
        StringBuilder sb = new StringBuilder();
        sb.append("hello world");
        for(int i=0; i<15; i++){
            sb.append(sb);
        }
        _client.add("sb", 86400, sb.toString());
        assertNotNull("null get when sb.length()="+sb.length(), _client.get("sb"));
        assertEquals("wrong length for sb",sb.length(), _client.get("sb").toString().length());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBigBinaryObject() throws ExecutionException, InterruptedException {
        Object bigObject = getBigObject();
        Future<Boolean> future = _client.set(KEY, TWO_WEEKS, bigObject);
        assertTrue(future.get());
        final Map<String, Double> map = (Map<String, Double>)_client.get(KEY);
        assertNotNull(map);
        for (String key : map.keySet()) {
            Integer kint = Integer.valueOf(key);
            Double val = map.get(key);
            assertEquals(val, kint/42.0, 0.0);
        }
    }

    @Test
    public void testCAS() throws Exception {
        Future<Boolean> future = _client.set("foo", 32000, 123);
        assertTrue(future.get());
        CASValue<Object> casValue = _client.gets("foo");
        
        assertNotNull(casValue);
        assertEquals( 123, casValue.getValue());

        CASResponse cr = _client.cas("foo", casValue.getCas(), 456);

        assertEquals(CASResponse.OK, cr);

        Future<Object> rf = _client.asyncGet("foo");

        assertEquals(456, rf.get());
    }

    @Test
    public void testCASAfterAdd() throws Exception {
        Future<Boolean> future = _client.add("foo", 32000, 123);
        assertTrue(future.get());
        CASValue<Object> casValue = _client.gets("foo"); // should not produce an error
        assertNotNull(casValue);
        assertEquals( 123, casValue.getValue());
    }

    @Test
    public void testAppendPrepend() throws Exception {
        Future<Boolean> future = _client.set("foo", 0, "foo");
        assertTrue(future.get());

        _client.append(0, "foo", "bar");
        assertEquals( "foobar", _client.get( "foo" ));
        _client.prepend(0, "foo", "baz");
        assertEquals( "bazfoobar", _client.get( "foo" ));
    }
    
    @Test
    public void testTouch() throws Exception {
	
	// TODO the TOUCH command *is* supported by the text protocol, but we don't
	// presently implement it.
	if(this.getProtocolMode() == ProtocolMode.TEXT) return;
	
	// no expiration at first
        Future<Boolean> future = _client.set("foo", 0, "foo");
        assertTrue("set should be successful", future.get());
        
        // ensure it's present in the cache
        assertEquals("cache should contain a value for key 'foo'", "foo", _client.get("foo"));

        // touch to expire in 3sec after it hits the server
        future = _client.touch("foo", 3);
        assertTrue("touch should be successful", future.get());
        
        // sleep 5 sec 
        Thread.sleep(5000);
        
        // should be expired
        assertNull("cache entry should be expired", _client.get("foo"));
    }
    
    @Test
    public void testGetAndTouch() throws Exception {
	
	// GAT is NOT supported by the text protocol
	if(this.getProtocolMode() == ProtocolMode.TEXT) return;
	
	// no expiration at first
        Future<Boolean> future = _client.set("foo", 0, "foo");
        assertTrue("set should be successful", future.get());

        // ensure it's present in the cache; expire in 3sec after it hits the server
        assertEquals("cache should contain a value for key 'foo'", "foo", _client.getAndTouch("foo", 3).getValue());
        
        // sleep 5 sec 
        Thread.sleep(5000);
        
        // should be expired
        assertNull("cache entry should be expired", _client.get("foo"));
    }

    @Test
    public void testBulkGet() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        ArrayList<String> allStrings = new ArrayList<String>();
        ArrayList<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
        
        final int NUM_BULK_KEYS = 5;
        
        for (int i = 0; i < NUM_BULK_KEYS; i++) {
            futures.add(_client.set("foo" + i, 360000, "bar" + i));
            allStrings.add("foo" + i);
        }

        // wait for all the sets to complete
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(5, TimeUnit.SECONDS));
        }

        // doing a regular get, we are just too slow for spymemcached's tolerances... for now
        Future<Map<String, Object>> future = _client.asyncGetBulk(allStrings);
        Map<String, Object> results = future.get();

        for (int i = 0; i < NUM_BULK_KEYS; i++) {
            assertEquals("bar" + i, results.get("foo" + i));
        }

    }

    protected static Object getBigObject() {
        final Map<String, Double> map = new HashMap<String, Double>();

        for (int i=0;i<13000;i++) {
            map.put(Integer.toString(i), i/42.0);
        }

        return map;
    }
    
    
    @Test
    public void testIncrementCounter() throws Exception {
	
	// uses GAT so no text protocol
	if(this.getProtocolMode() == ProtocolMode.TEXT) return;
	
        String key = UUID.randomUUID().toString();
        Long value = getCounter(key);
        // should get back 1 as the default since it doesn't exist yet
        Assert.assertEquals(1L, value.longValue());

        value = incrementCounter(key);
        // should get back 2 as the default since it doesn't exist yet
        Assert.assertEquals(2L, value.longValue());

        value = incrementCounter(key);
        Assert.assertEquals(3L, value.longValue());

        value = getCounter(key);
        Assert.assertEquals(3L, value.longValue());
    }
    
    int cacheTimeInS = 15 * 60; // 15 minutes
    
    /* This test case fails in a client project */
    public Long incrementCounter(String key) throws Exception {
        //try {
            return _client.incr(key, 1, 2, cacheTimeInS);
        //} catch (Exception e) {
        //    return 1L;
        //}
    }

    public Long getCounter(String key) {
        CASValue<Object> value = _client.getAndTouch(key, cacheTimeInS);
        // default transcoder returns the value as a string
        try {
            return value == null ? 1 : Long.parseLong((String) value.getValue());
        } catch (NumberFormatException ignored) {
            return 1L;
        }
    }


}
