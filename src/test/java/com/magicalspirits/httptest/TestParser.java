package com.magicalspirits.httptest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.magicalspirits.httptest.httpapplication.ApplicationRunner;
import com.magicalspirits.httptest.httpparser.HttpRuriData;
import com.magicalspirits.httptest.httpparser.HttpRuriParser;
import com.mycila.guice.ext.closeable.CloseableInjector;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;

public class TestParser 
{
	private static CloseableInjector i;
	private static int port = 0;
	
	private static ExecutorService exec;

	@BeforeClass
	public static void setup()
	{
		i = Guice.createInjector(
				new CloseableModule(), new Jsr250Module(), new ExecutorsModule(), 
				new MetricsModule(), new TestlineModule(HttpRuriParser.class, ApplicationRunnerTestImpl.class))
					.getInstance(CloseableInjector.class);
		
		port = i.getInstance(ServerSocket.class).getLocalPort();
		exec = i.getInstance(ExecutorService.class);
	}

	@Test
	@SneakyThrows
	public void testSingleParse()
	{
		try(Socket s = new Socket("localhost", port))
		{
			PrintStream ps = new PrintStream(s.getOutputStream(), true);
			ps.println("GET /test HTTP/1.0");
			ps.println("SomeHeader: someValue");
			ps.println("SomeHeaderTwoRow: someValue1");
			ps.println("SomeHeaderTwoRow: someValue2");
			ps.println("SomeHeaderCommas: someValue1,someValue2");
			ps.println("SomeHeaderCommasAndRows: someValue1,someValue2");
			ps.println("SomeHeaderCommasAndRows: someValue3");
			ps.println("SomeHeaderCommasAndRows: someValue4,someValue5");
			ps.println();
			ps.flush();
			
			BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1));

			String text = br.readLine();
			assertEquals("200 OK", text);
			assertNotNull(ApplicationRunnerTestImpl.getLastConstructed());
			assertEquals("GET", ApplicationRunnerTestImpl.getLastConstructed().getHttpRuri().getRequestType());
			assertEquals("/test", ApplicationRunnerTestImpl.getLastConstructed().getHttpRuri().getRuriPath());
			assertEquals("HTTP/1.0", ApplicationRunnerTestImpl.getLastConstructed().getHttpRuri().getVersion());

			assertEquals("someValue", ApplicationRunnerTestImpl.getLastConstructed().getHeaders().get("SomeHeader").get(0));
			assertEquals("someValue1", ApplicationRunnerTestImpl.getLastConstructed().getHeaders().get("SomeHeaderTwoRow").get(0));
			assertEquals("someValue2", ApplicationRunnerTestImpl.getLastConstructed().getHeaders().get("SomeHeaderTwoRow").get(1));

			assertEquals("someValue1", ApplicationRunnerTestImpl.getLastConstructed().getHeaders().get("SomeHeaderCommas").get(0));
			assertEquals("someValue2", ApplicationRunnerTestImpl.getLastConstructed().getHeaders().get("SomeHeaderCommas").get(1));
			for(int i = 1; i <= 5; i++)
				assertEquals("someValue" + i, ApplicationRunnerTestImpl.getLastConstructed().getHeaders().get("SomeHeaderCommasAndRows").get(i-1));
		}
	}

	@Test(timeout=500) //500 should be plenty. Test takes 0.22 seconds on my mac. Your results may vary
	@SneakyThrows
	public void testOneHundredParses() //this is almost simultaneous, and is about all the IP stack on my mac can handle without tuning
	{
		List<Callable<Optional<Exception>>> callables = Lists.newArrayList();
		
		for(int i = 0; i < 100; i++)
		{
			callables.add(() ->
			{
				try(Socket s = new Socket("localhost", port))
				{
					PrintStream ps = new PrintStream(s.getOutputStream(), true);
					ps.println("GET /test HTTP/1.0");
					ps.println("SomeHeader: someValue");
					ps.println("SomeHeaderTwoRow: someValue1");
					ps.println("SomeHeaderTwoRow: someValue2");
					ps.println("SomeHeaderCommas: someValue1,someValue2");
					ps.println("SomeHeaderCommasAndRows: someValue1,someValue2");
					ps.println("SomeHeaderCommasAndRows: someValue3");
					ps.println("SomeHeaderCommasAndRows: someValue4,someValue5");
					ps.println();
					ps.flush();

					BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1));
		
					String text = br.readLine();
					
					assertEquals("200 OK", text);
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
	
	public static class ApplicationRunnerTestImpl implements ApplicationRunner
	{
		public static final String PONG = "pong";

		public static final String PING = "ping";

		@Getter
		private static ApplicationRunnerTestImpl lastConstructed;

		@Getter
		private boolean run = false;

		@Getter
		@Setter(onMethod=@__(@Override))
		private Socket socket;

		@Getter
		@Setter(onMethod=@__(@Override))
		private BufferedReader bufferedReader;
		
		@Getter
		@Setter(onMethod=@__(@Override))
		private HttpRuriData httpRuri;

		@Getter
		@Setter(onMethod=@__(@Override))
		private ArrayListMultimap<String, String> headers;
		
		public ApplicationRunnerTestImpl() 
		{
			ApplicationRunnerTestImpl.lastConstructed = this;
		}

		@Override 
		@SneakyThrows
		public void run() 
		{
			PrintStream ps = new PrintStream(socket.getOutputStream(), true);
			ps.println("200 OK");
			ps.flush();
			socket.close();
		}	
	}
	
	@AfterClass
	public static void shutdown()
	{
		i.close();
	}
}
