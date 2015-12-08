/**
 * 
 */
package uk.bl.wap.util;

import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Required;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

/**
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class EhcacheRecentlySeenUriUniqFilter
        extends RecentlySeenUriUniqFilter {

    /** */
    private static final long serialVersionUID = 7156746218148487509L;

    private static Logger LOGGER = Logger
            .getLogger(EhcacheRecentlySeenUriUniqFilter.class.getName());

    private String cacheStorePath = System.getProperty("java.io.tmpdir");
    private CacheManager manager;
    private Ehcache cache;

    private int maxEntriesLocalHeap = 1000 * 1000;

    public EhcacheRecentlySeenUriUniqFilter() {
        super();
    }

    /**
     * Initializer.
     */
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        this.setupCache();
    }

    /**
     * 
     * @return
     */
    public String getCacheStorePath() {
        return cacheStorePath;
    }

    /**
     * 
     * @param cacheStorePath
     */
    @Required
    public void setCacheStorePath(String cacheStorePath) {
        this.cacheStorePath = cacheStorePath;
    }

    /**
     * 
     * @return
     */
    public Ehcache getCache() {
        if (cache == null || !cache.getStatus().equals(Status.STATUS_ALIVE)) {
            setupCache();
        }
        return cache;
    }

    /**
     * 
     * @param cache
     */
    public void setCache(Ehcache cache) {
        this.cache = cache;
    }

    /**
     * 
     */
    private void setupCache() {
        LOGGER.info("Setting up cache...");
        if (manager == null
                || !manager.getStatus().equals(Status.STATUS_ALIVE)) {
            Configuration configuration = new Configuration();
            DiskStoreConfiguration diskStoreConfiguration = new DiskStoreConfiguration();
            diskStoreConfiguration.setPath(cacheStorePath);
            // Already created a configuration object ...
            configuration.addDiskStore(diskStoreConfiguration);
            manager = CacheManager.create(configuration);
        }
        // Get existing cache if there is one:
        cache = manager.getEhcache("recentlySeenUrls");
        // Otherwise, make a new one:
        if (cache == null) {
            LOGGER.info("Setting up default Ehcache configuration.");
            cache = new Cache(new CacheConfiguration("recentlySeenUrls",
                    maxEntriesLocalHeap)
                            .memoryStoreEvictionPolicy(
                                    MemoryStoreEvictionPolicy.LFU)
                            .eternal(false).timeToLiveSeconds(this.defaultTTL)
                            .diskExpiryThreadIntervalSeconds(0)
                            .diskPersistent(true).overflowToDisk(true));
            manager.addCache(cache);
        }
    }

    /**
     * 
     */
    protected boolean setAdd(CharSequence uri) {
        // Build the cache entry:
        Element element = new Element(uri, uri);

        // Allow entries to expire after a while, defaults, ranges, etc,
        // surt-prefixed.
        element.setTimeToLive(getTTLForUrl(uri.toString()));

        // Add to the cache, if absent:
        Element added = getCache().putIfAbsent(element);
        if (added == null) {
            LOGGER.finest("Cache entry " + uri + " is new.");
        } else {
            LOGGER.finest("Cache entry " + uri + " is already in the cache.");
        }

        return (added == null);
    }

    /**
     * 
     */
    protected boolean setRemove(CharSequence uri) {
        return getCache().remove(uri);
    }

    /**
     * 
     */
    protected long setCount() {
        return getCache().getSize();
    }

    @Override
    public long requestFlush() {
        this.getCache().flush();
        return 0;
    }

    private void closeEhcache() {
        LOGGER.info("Shutting down the cache...");
        if (this.cache != null) {
            this.cache.flush();
        }
        if (this.manager != null
                && this.manager.getStatus().equals(Status.STATUS_ALIVE)) {
            this.manager.shutdown();
        }
    }

    @Override
    public void close() {
        this.closeEhcache();
        super.close();
    }

    @Override
    protected void finalize() throws Throwable {
        this.closeEhcache();
        super.finalize();
    }

}
