package com.magicalspirits.httptest;

import java.util.function.Supplier;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.magicalspirits.httptest.acceptor.SocketRunnerImpl;

public class MainlineModule extends AbstractModule 
{
	@Override
	protected void configure() 
	{
		bind(SocketRunner.class).to(SocketRunnerImpl.class);
	}
	
	/**
	 * This supplier is guaranteed to always return a new instance.
	 */
	@Provides
	public Supplier<SocketRunner> getSocketRunnerSupplier(final Injector i)
	{
		return () -> i.getInstance(SocketRunner.class);
	}
}
