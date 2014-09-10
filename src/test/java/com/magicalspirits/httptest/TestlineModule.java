package com.magicalspirits.httptest;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.magicalspirits.httptest.acceptor.AcceptorService;
import com.magicalspirits.httptest.acceptor.ServerSocketAcceptor;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.magicalspirits.httptest.httpapplication.ApplicationRunner;
import com.magicalspirits.httptest.httpparser.HttpHeaderParser;

@Slf4j
@RequiredArgsConstructor
public class TestlineModule extends AbstractModule 
{
	private final Class<? extends SocketRunner> socketRunner;
	
	private final Class<? extends ApplicationRunner> applicationRunner;
	
	private MainlineModule mmm = new MainlineModule(); //reference for delegation
	
	@Override
	protected void configure() 
	{
		bind(SocketRunner.class).to(socketRunner);
		bind(AcceptorService.class).asEagerSingleton();
		bind(ApplicationRunner.class).to(applicationRunner);
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<SocketRunner> getSocketRunnerSupplier(final Injector i)
	{
		return mmm.getSocketRunnerSupplier(i);
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<HttpHeaderParser> getHeaderParser(final Injector i)
	{
		return mmm.getHeaderParser(i);
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<ApplicationRunner> getApplicationRunner(final Injector i)
	{
		return mmm.getApplicationRunner(i);
	}
	
	@Provides
	@Singleton
	public ServerSocket getServerSocket()
	{
		try 
		{
			//Note: 0 means any available high port. This is valuable for testing, as it wont conflict
			// with other services running on the computer building and testing this service.
			return new ServerSocket(0);
		} 
		catch (IOException e) 
		{
			log.error("Unable to open server socket", e);
			throw new RuntimeException(e);
		}
	}
	
	@Provides
	@Singleton
	public List<ServerSocketAcceptor> getAcceptors(ServerSocketAcceptor ss1, ServerSocketAcceptor ss2)
	{
		return mmm.getAcceptors(ss1, ss2);
	}

	
	@Provides
	@Singleton
	public Map<String, String> getMimeTypeRegistry()
	{
		return mmm.getMimeTypeRegistry();
	}
}
