package org.hisp.dhis.cache;

/*
 * Copyright (c) 2004-2017, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.hisp.dhis.external.conf.ConfigurationKey;
import org.hisp.dhis.external.conf.DhisConfigurationProvider;
import org.springframework.beans.factory.annotation.Autowired;

import net.spy.memcached.MemcachedClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Lars Helge Overland
 */
public class DefaultHibernateCacheManager
    implements HibernateCacheManager
{
    private static final Log log = LogFactory.getLog( DefaultHibernateCacheManager.class );
    
    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private SessionFactory sessionFactory;

    public void setSessionFactory( SessionFactory sessionFactory )
    {
        this.sessionFactory = sessionFactory;
    }
    
    @Autowired
    private DhisConfigurationProvider config;

    // -------------------------------------------------------------------------
    // HibernateCacheManager implementation
    // -------------------------------------------------------------------------

    @Override
    public void clearObjectCache()
    {
        sessionFactory.getCache().evictEntityRegions();
        sessionFactory.getCache().evictCollectionRegions();
        
        clearMemcachedCache();
     }
    
    @Override
    public void clearQueryCache()
    {
        sessionFactory.getCache().evictDefaultQueryRegion();
        sessionFactory.getCache().evictQueryRegions();
    }
    
    @Override
    public void clearCache()
    {
        clearObjectCache();        
        clearQueryCache();
        
        log.info( "Cleared Hibernate caches" );
    }
    
    @Override
    public Statistics getStatistics()
    {
        return sessionFactory.getStatistics();
    }
    
    /**
     * Clears {@code memcached} cache, given that it has been configured as
     * application cache.
     * <p>
     * TODO This is a terrible hack to work around an issue
     * with the second level cache provider. Will be removed when fixed there.
     * <p>
     * {@linkplain https://github.com/mihaicostin/hibernate-l2-memcached/issues/9}
     */
    private void clearMemcachedCache()
    {
        if ( config.isMemcachedCacheProviderEnabled() )
        {
            getCache().flush();
        }
    }
    
    private MemcachedClient getCache()
    {
        String cacheServers = config.getProperty( ConfigurationKey.CACHE_SERVERS );
        String[] cacheArray = cacheServers.split( ":" );
        String url = cacheArray[0];
        int port = cacheArray.length == 2 ? Integer.parseInt( cacheArray[1] ) : 11211;
        
        try
        {
            return new MemcachedClient( new InetSocketAddress( url, port ) );
        }
        catch( IOException ex )
        {
            throw new UncheckedIOException( ex );
        }
    }
}
