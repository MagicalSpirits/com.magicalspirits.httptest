package com.magicalspirits.httptest;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
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
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.magicalspirits.httptest.acceptor.ServerSocketAcceptor;
import com.magicalspirits.httptest.httpapplication.ServeHttpFile;
import com.magicalspirits.httptest.httpparser.HttpRuriParser;
import com.magicalspirits.httptest.launcher.ExecutorsModule;
import com.magicalspirits.httptest.metricsmonitoring.MetricsModule;
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

	@Test(timeout=3000) //3000ms should be plenty. Test takes 0.706 seconds on my mac. Your results may vary
	public void testOneThousandRequestsInSequence() throws IOException
	{
		for(int i = 0; i < 1000; i++)
			testTextData();
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
	
	@Test(timeout=1000) //1000ms should be plenty. Test takes 0.086 seconds on my mac. Your results may vary
	@SneakyThrows
	public void testFiftyRequests() //this is almost simultaneous, and is about all the IP stack on my mac can handle without tuning
	{
		//Note: The guava utility says to maintain keep alive, and then it closes the socket, so we'll see lots of socket
		// closed before first request when it returns on the http 1.1 keep alive.
		final String fromLocal = Resources.toString(Resources.getResource("wwwroot/testfile1.txt"), Charsets.UTF_8);

		List<Callable<Optional<Exception>>> callables = Lists.newArrayList();
		
		for(int i = 0; i < 50; i++)
		{
			callables.add(() ->
			{
				try
				{
					String fromServer = Resources.toString(new URL("http://localhost:" + port + "/testfile1.txt"), Charsets.UTF_8);
					assertEquals(fromLocal, fromServer);
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
	
	@Test
	public void testHttp11KeepAlive() throws IOException
	{
		String fromLocal = Resources.toString(Resources.getResource("wwwroot/testfile1.txt"), Charsets.UTF_8);

		int expectedConnections = ServerSocketAcceptor.getNumberOfSocketsAccepted() + 1;
		
		for(int i = 0; i < 5; i++)
		{
			HttpURLConnection connection = (HttpURLConnection) (new URL("http://localhost:" + port + "/testfile1.txt")).openConnection();
			String fromServer = CharStreams.toString(new InputStreamReader(connection.getInputStream(), Charsets.UTF_8));
		
			assertEquals(fromLocal, fromServer);
			assertEquals(expectedConnections, ServerSocketAcceptor.getNumberOfSocketsAccepted());
		}
	}

	@Test(expected=FileNotFoundException.class)
	public void test404() throws IOException
	{
		HttpURLConnection connection = (HttpURLConnection) (new URL("http://localhost:" + port + "/nosuchfile.txt")).openConnection();
		assertEquals(404, connection.getResponseCode());
		CharStreams.toString(new InputStreamReader(connection.getInputStream(), Charsets.UTF_8));
	}

	
	@AfterClass
	public static void shutdown()
	{
		i.close();
	}
}
