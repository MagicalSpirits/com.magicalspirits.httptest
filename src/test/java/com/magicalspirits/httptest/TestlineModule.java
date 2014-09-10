package com.magicalspirits.httptest;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.function.Supplier;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.magicalspirits.httptest.acceptor.AcceptorService;
import com.magicalspirits.httptest.acceptor.ServerSocketAcceptor;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.magicalspirits.httptest.httpapplication.ApplicationRunner;
import com.magicalspirits.httptest.httpapplication.ServeHttpFile;
import com.magicalspirits.httptest.httpparser.HttpHeaderParser;

@Slf4j
@AllArgsConstructor
public class TestlineModule extends AbstractModule 
{
	private Class<? extends SocketRunner> socketRunner;
	
	@Override
	protected void configure() 
	{
		bind(SocketRunner.class).to(socketRunner);
		bind(AcceptorService.class).asEagerSingleton();
		bind(ApplicationRunner.class).to(ServeHttpFile.class);
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<SocketRunner> getSocketRunnerSupplier(final Injector i)
	{
		return () -> i.getInstance(SocketRunner.class);
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<HttpHeaderParser> getHeaderParser(final Injector i)
	{
		return () -> i.getInstance(HttpHeaderParser.class);
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<ApplicationRunner> getApplicationRunner(final Injector i)
	{
		return () -> i.getInstance(ApplicationRunner.class);
	}
	
	@Provides
	@Singleton
	public ServerSocket getServerSocket()
	{
		try 
		{
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
		return Lists.newArrayList(ss1, ss2);
	}
	
	@Provides
	@Named(ExecutorsModule.SHUTDOWN_TIME_SECONDS)
	@Singleton
	public int getShutdownInSeconds()
	{
		return 1;
	}
}
