package com.magicalspirits.httptest;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.function.Supplier;

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
import com.magicalspirits.httptest.acceptor.SocketRunnerImpl;

@Slf4j
public class MainlineModule extends AbstractModule 
{
	@Override
	protected void configure() 
	{
		bind(SocketRunner.class).to(SocketRunnerImpl.class);
		bind(AcceptorService.class).asEagerSingleton();
	}
	
	@Provides
	@Singleton
	public ServerSocket getServerSocket()
	{
		//Note: Port could come from config, or system properties, or wherever. This wouldn't be hardcoded on a prod system
		try 
		{
			return new ServerSocket(8080);
		} 
		catch (IOException e) 
		{
			log.error("Unable to open server socket", e);
			System.exit(1);
			return null; //for the compiler.
		}
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<SocketRunner> getSocketRunnerSupplier(final Injector i)
	{
		return () -> i.getInstance(SocketRunner.class);
	}
	
	@Provides
	@Singleton
	public List<ServerSocketAcceptor> getAcceptors(ServerSocketAcceptor ss1, ServerSocketAcceptor ss2)
	{
		//a production instance really shouldn't need more than 2 unless something in wrong with the handoff to the executor service.
		return Lists.newArrayList(ss1, ss2);
	}
	
	@Provides
	@Named(ExecutorsModule.SHUTDOWN_TIME_SECONDS)
	@Singleton
	public int getShutdownInSeconds()
	{
		return 30;
	}
}
