package com.magicalspirits.httptest;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.codahale.metrics.JvmAttributeGaugeSet;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import com.codahale.metrics.json.HealthCheckModule;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;

@Slf4j
public class MetricsModule extends MetricsInstrumentationModule 
{
	private static MetricRegistry registry;

	public MetricsModule() 
	{
		super(MetricsModule.createMetricRegistry());
		//this is quite crazy, but the MetricsInstrumentationModule stores the registry in a private
		// variable, and since it has to be passed in the constructor, I can't get a reference to it to return as 
		// a binding.
		// There should only be one of these app wide in my app, so I am storing it in a static variable and using it in the provides
		// I'm going to submit a pull request against the codahale subproject for this nonsense later.
	}

	protected synchronized static MetricRegistry createMetricRegistry()
	{
		if(registry != null)
			return registry;

		registry = new MetricRegistry();

		//a bunch of the following code I learned from 
		// https://github.com/cloudera/cdk/blob/master/cdk-morphlines/cdk-morphlines-metrics-servlets/src/main/java/com/cloudera/cdk/morphline/metrics/servlets/RegisterJVMMetricsBuilder.java
		BufferPoolMetricSet bpms = new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer());
		registerAll("jvm.buffers", bpms, registry);
		registerAll("jvm.gc", new GarbageCollectorMetricSet(), registry);
		registerAll("jvm.memory", new MemoryUsageGaugeSet(), registry);
		registerAll("jvm.threads", new ThreadStatesGaugeSet(), registry);
		register("jvm.fileDescriptorCountRatio", new FileDescriptorRatioGauge(), registry);

		registry.registerAll(new JvmAttributeGaugeSet());

		return registry;
	}

	@Provides
	@Singleton
	public ObjectMapper getMapper()
	{
		return new ObjectMapper()
			.registerModule(
				new com.codahale.metrics.json.MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false))
			.registerModule(new HealthCheckModule());
	}
	
	@Provides
	@Singleton
	public HealthCheckRegistry getHealthCheckRegistry()
	{
		HealthCheckRegistry hr = new HealthCheckRegistry();
		//only add deadlock checker here
		hr.register("deadlocks", new ThreadDeadlockHealthCheck());
		return hr;
	}
	
	@Provides
	@Singleton
	public MetricRegistry getMetricRegistry()
	{
		return registry;
	}
	

	private static void registerAll(String prefix, MetricSet ms, MetricRegistry mr) 
	{
		for (Map.Entry<String, Metric> entry : ms.getMetrics().entrySet()) 
		{
			String name = MetricRegistry.name(prefix, entry.getKey());

			if (entry.getValue() instanceof MetricSet) 
			{
				registerAll(name, (MetricSet) entry.getValue(), mr);
			} 
			else 
			{
				register(name, entry.getValue(), mr);
			}
		} 
	}

	private static void register(String name, Metric m, MetricRegistry mr) 
	{
		if (!mr.getMetrics().containsKey(name)) 
		{ 
			try 
			{
				mr.register(name, m);
			} 
			catch (IllegalArgumentException e) 
			{
				log.warn("Unable to add metric {}", name, e);
			}
		}
	}
}
