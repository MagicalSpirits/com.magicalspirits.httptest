package com.magicalspirits.httptest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import lombok.SneakyThrows;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.magicalspirits.httptest.httpapplication.ServeHttpFile;
import com.magicalspirits.httptest.httpparser.HttpRuriParser;
import com.mycila.guice.ext.closeable.CloseableInjector;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;

public class SystemTest 
{
	private static CloseableInjector i;
	private static int port = 0;
	
	private static ExecutorService exec;

	@BeforeClass
	public static void setup()
	{
		i = Guice.createInjector(
				new CloseableModule(), new Jsr250Module(), new ExecutorsModule(), 
				new MetricsModule(), new TestlineModule(HttpRuriParser.class, ServeHttpFile.class))
					.getInstance(CloseableInjector.class);
		
		port = i.getInstance(ServerSocket.class).getLocalPort();
		exec = i.getInstance(ExecutorService.class);
	}
	
	@Test
	public void testTextData() throws IOException
	{
		String fromServer = Resources.toString(new URL("http://localhost:" + port + "/testfile1.txt"), Charsets.UTF_8);
		
		String fromLocal = Resources.toString(Resources.getResource("wwwroot/testfile1.txt"), Charsets.UTF_8);
		
		assertEquals(fromLocal, fromServer);
	}

	@Test
	public void testBinaryData() throws IOException
	{
		byte[] fromServer = Resources.toByteArray(new URL("http://localhost:" + port + "/binarydata.bin"));
		
		byte[] fromLocal = Resources.toByteArray(Resources.getResource("wwwroot/binarydata.bin"));
		
		assertArrayEquals(fromLocal, fromServer);
	}
	
	
	//On these next two tests, our goal isn't to test codahale, but just to ensure the binding is working
	@Test
	public void testMonitoring() throws IOException
	{
		String fromServer = Resources.toString(new URL("http://localhost:" + port + "/monitoring"), Charsets.UTF_8);
		
		assertTrue(fromServer.contains("\"healthy\" : true"));
	}

	@Test
	public void testMetrics() throws IOException
	{
		String fromServer = Resources.toString(new URL("http://localhost:" + port + "/metrics"), Charsets.UTF_8);
		
		assertTrue(fromServer.contains("com.magicalspirits.httptest.httpparser.HttpRuriParser.run.meter"));
	}
	
	
	@Test(timeout=1000) //1000ms should be plenty. Test takes 0.086 secdonds on my mac. Your results may vary
	@SneakyThrows
	public void testOneHundredRequests() //this is almost simultaneous, and is about all the IP stack on my mac can handle without tuning
	{
		List<Callable<Optional<Exception>>> callables = Lists.newArrayList();
		
		for(int i = 0; i < 100; i++)
		{
			callables.add(() ->
			{
				try
				{
					String value = Resources.toString(new URL("http://localhost:" + port + "/testfile1.txt"), Charsets.UTF_8);
					assertFalse(Strings.isNullOrEmpty(value));
				}
				catch(Exception e)
				{
					return Optional.of(e);
				}
				return Optional.empty();
			});
		}
		
		List<Future<Optional<Exception>>> futures = Lists.newArrayList();
		
		//list of callables primed. Fire it up.
		for(Callable<Optional<Exception>> callable : callables)
			futures.add(exec.submit(callable));
		
		//list of executed. get the future on each one.
		for(Future<Optional<Exception>> future : futures)
		{
			Optional<Exception> result = future.get(10, TimeUnit.SECONDS);
			if(result.isPresent())
				throw result.get();
		}
	}
	
	@AfterClass
	public static void shutdown()
	{
		i.close();
	}
}
