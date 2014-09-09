package com.magicalspirits.httptest;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.LoggerFactory;

import com.codahale.metrics.JvmAttributeGaugeSet;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.FileDescriptorRatioGauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.palominolabs.metrics.guice.InstrumentationModule;

@Slf4j
public class MetricsModule extends InstrumentationModule 
{
	@Override
	protected MetricRegistry createMetricRegistry()
	{
		MetricRegistry mr = new MetricRegistry();

		//a bunch of the following code I learned from 
		// https://github.com/cloudera/cdk/blob/master/cdk-morphlines/cdk-morphlines-metrics-servlets/src/main/java/com/cloudera/cdk/morphline/metrics/servlets/RegisterJVMMetricsBuilder.java
		BufferPoolMetricSet bpms = new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer());
		registerAll("jvm.buffers", bpms, mr);
		registerAll("jvm.gc", new GarbageCollectorMetricSet(), mr);
		registerAll("jvm.memory", new MemoryUsageGaugeSet(), mr);
		registerAll("jvm.threads", new ThreadStatesGaugeSet(), mr);
		register("jvm.fileDescriptorCountRatio", new FileDescriptorRatioGauge(), mr);

		mr.registerAll(new JvmAttributeGaugeSet());

		Slf4jReporter reporter = Slf4jReporter.forRegistry(mr)
				.outputTo(LoggerFactory.getLogger("com.magicalspirits.httptest.metrics"))
				.convertRatesTo(TimeUnit.SECONDS)
				.convertDurationsTo(TimeUnit.MILLISECONDS).build();

		reporter.start(15, TimeUnit.SECONDS);
		return mr;
	}

	private void registerAll(String prefix, MetricSet ms, MetricRegistry mr) 
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

	private void register(String name, Metric m, MetricRegistry mr) 
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
