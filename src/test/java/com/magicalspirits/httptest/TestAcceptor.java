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
import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.mycila.guice.ext.closeable.CloseableInjector;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;

public class TestAcceptor 
{
	private static CloseableInjector i;
	private static int port = 0;
	
	private static ExecutorService exec;

	@BeforeClass
	public static void setup()
	{
		i = Guice.createInjector(
				new CloseableModule(), new Jsr250Module(), new ExecutorsModule(), 
				new MetricsModule(), new TestlineModule(SocketAcceptorTestImpl.class))
					.getInstance(CloseableInjector.class);
		
		port = i.getInstance(ServerSocket.class).getLocalPort();
		exec = i.getInstance(ExecutorService.class);
	}

	@Test
	@SneakyThrows
	public void testSingleAccept()
	{
		try(Socket s = new Socket("localhost", port))
		{
			PrintStream ps = new PrintStream(s.getOutputStream(), true);
			ps.println(SocketAcceptorTestImpl.PING);
			BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1));

			String text = br.readLine();
			
			assertEquals(SocketAcceptorTestImpl.PONG, text);
			assertNotNull(SocketAcceptorTestImpl.getLastConstructed());
			assertNotNull(SocketAcceptorTestImpl.getLastConstructed().getSocket());
		}
	}

	@Test(timeout=300) //300ms should be plenty. Test takes 0.04 secdonds on my mac. Your results may vary
	@SneakyThrows
	public void testOneHundredAccepts() //this is almost simultaneous, and is about all the IP stack on my mac can handle without tuning
	{
		List<Callable<Optional<Exception>>> callables = Lists.newArrayList();
		
		for(int i = 0; i < 100; i++)
		{
			callables.add(() ->
			{
				try(Socket s = new Socket("localhost", port))
				{
					PrintStream ps = new PrintStream(s.getOutputStream(), true);
					ps.println(SocketAcceptorTestImpl.PING);
					BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1));
		
					String text = br.readLine();
					
					assertEquals(SocketAcceptorTestImpl.PONG, text);
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
	
	public static class SocketAcceptorTestImpl implements SocketRunner
	{
		public static final String PONG = "pong";

		public static final String PING = "ping";

		@Getter
		private static SocketAcceptorTestImpl lastConstructed;

		@Getter
		private boolean run = false;

		@Getter
		@Setter(onMethod=@__(@Override))
		private Socket socket;

		@Setter(onMethod=@__(@Override))
		private BufferedReader bufferedReader;
		
		@Getter
		private String text;

		public SocketAcceptorTestImpl() 
		{
			SocketAcceptorTestImpl.lastConstructed = this;
		}

		@Override 
		@SneakyThrows
		public void run() 
		{
			this.run = true;
			text = bufferedReader.readLine();
			
			PrintStream ps = new PrintStream(socket.getOutputStream(), true);
			if(text.equalsIgnoreCase(PING))
			{
				ps.println(PONG);
			}
			else
			{
				ps.println("bogus");
			}
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