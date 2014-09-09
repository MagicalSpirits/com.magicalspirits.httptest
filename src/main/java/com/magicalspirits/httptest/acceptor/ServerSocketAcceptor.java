package com.magicalspirits.httptest.acceptor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.magicalspirits.httptest.ExecutorsModule;

@AllArgsConstructor @NoArgsConstructor
public class ServerSocketAcceptor implements Runnable
{
	private boolean running = true;
	
	@PreDestroy
	public void stop()
	{
		running = false;
	}
	
	@Inject
	private ServerSocket listeningSocket;
	
	@Inject
	@Named(ExecutorsModule.HTTP_SERVER_POOL)
	private ExecutorService serverPool;

	@Inject
	private Supplier<SocketRunner> socketRunnerSupplier;
	
	@Override
	public void run() 
	{
		try
		{
			while(running)
			{
				Socket s = listeningSocket.accept();
				SocketRunner sr = socketRunnerSupplier.get();
				sr.setSocket(s);
				serverPool.submit(sr);
			}
		}
		catch (IOException e) 
		{
			throw new RuntimeException(e); //send it to the registered uncaught exception handler 
		}
		finally
		{
			if(running)
			{
				//we're here because an exception has occurred. We want to re-add this to the system pool, 
				//but let the exception flow out to the registered uncaught exception handler
				//this might cause unlimited errors over and over again, however the counter risk is that
				//we stop executing something we should be handling....
				serverPool.submit(this);
			}
		}
	}
}
