package com.magicalspirits.httptest;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.magicalspirits.httptest.thirdparty.InstrumentedExecutorService;

public class ExecutorsModule extends AbstractModule 
{
	public static final String SHUTDOWN_TIME_SECONDS = "shutdown-time-seconds";
	public static final String HTTP_SERVER_POOL = "http-server-pool";

	@Override
	protected void configure() 
	{
		bind(ExecutorsService.class).asEagerSingleton();
	}
	
	@Provides
	@Singleton
	public ExecutorService getDefaultExecutorService(@Named(SHUTDOWN_TIME_SECONDS) int shutdownInSeconds, MetricRegistry registry, UncaughtExceptionHandler uncaughtExceptionHandler)
	{
		//Note: I dont know the performance profile I'm aiming for here, so we'll leave it
		//unbounded. This would be the place to make it bounded if had some specific settings
		return getService(shutdownInSeconds, "default-pool", registry, uncaughtExceptionHandler);
	}
	
	@Provides
	@Named(HTTP_SERVER_POOL)
	@Singleton
	public ExecutorService getServerPoolExecutorService(@Named(SHUTDOWN_TIME_SECONDS) int shutdownInSeconds, MetricRegistry registry, UncaughtExceptionHandler uncaughtExceptionHandler)
	{
		//Note: I dont know the performance profile I'm aiming for here, so we'll leave it
		//unbounded. This would be the place to make it bounded if had some specific settings
		return getService(shutdownInSeconds, HTTP_SERVER_POOL, registry, uncaughtExceptionHandler);
	}
	
	@Provides
	@Singleton
	public UncaughtExceptionHandler getUncaughtExceptionHandler(LoggingUncaughtExceptionHandler internal)
	{
		return internal;
	}
	
	
	private InstrumentedExecutorService getService(int shutdownInSeconds, String poolName, MetricRegistry registry, UncaughtExceptionHandler uncaughtExceptionHandler)
	{
		ExecutorService es = Executors.newCachedThreadPool(
				new ThreadFactoryBuilder().setNameFormat(poolName + "-%d").setDaemon(true)
					.setUncaughtExceptionHandler(uncaughtExceptionHandler).build());
		MoreExecutors.addDelayedShutdownHook(es, shutdownInSeconds, TimeUnit.SECONDS);
		return new InstrumentedExecutorService(es, registry);	
	}
	
	public static class ExecutorsService
	{
		@Inject
		@Named(ExecutorsModule.HTTP_SERVER_POOL)
		private ExecutorService serverPool;
		
		@Inject
		private ExecutorService defaultPool;
		
		
		@PreDestroy
		public void shutdown()
		{
			//shutdwn the pools to prevent future work from happening on this instance.
			serverPool.shutdown();
			defaultPool.shutdown();
		}
	}
}
