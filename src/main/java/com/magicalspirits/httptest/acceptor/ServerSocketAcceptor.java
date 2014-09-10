package com.magicalspirits.httptest.acceptor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.annotation.PreDestroy;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.google.common.base.Charsets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.magicalspirits.httptest.ExecutorsModule;

@AllArgsConstructor 
@NoArgsConstructor
public class ServerSocketAcceptor implements Runnable
{
	private boolean running = true;
	
	@PreDestroy
	public void stop()
	{
		running = false;
		executing.interrupt();
	}
	
	private Thread executing;
	
	@Inject
	private ServerSocket listeningSocket;
	
	@Inject
	@Named(ExecutorsModule.HTTP_SERVER_POOL)
	private ExecutorService serverPool;

	@Inject
	private Supplier<SocketRunner> socketRunnerSupplier;
	
	//for testing
	@Getter
	private static volatile int numberOfSocketsAccepted = 0;
	
	@Override
	public void run() 
	{
		this.executing = Thread.currentThread();
		try
		{
			while(running && !serverPool.isShutdown())
			{
				Socket s = listeningSocket.accept();
				numberOfSocketsAccepted++;
				s.setSoTimeout(10000); //Note: This should probably be an injected config variable, or a system property. Hardcoding for this demo.
				SocketRunner sr = socketRunnerSupplier.get();
				sr.setSocket(s);
				//ISO 8859-1 is somehow the RFC defined encoding for the http body.
				sr.setBufferedReader(new BufferedReader(new InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1)));
				serverPool.submit(sr);
			}
		}
		catch (Exception e) 
		{
			if(running && !serverPool.isShutdown())
			{
				//we're here because an exception has occurred. I want to re-add this to the system pool, 
				//but let the exception flow out to the registered uncaught exception handler
				//this might cause unlimited errors over and over again, however the alternate risk is that
				//we stop executing something we should be handling....
				serverPool.submit(this);
			}
			if(running || !(running && e instanceof InterruptedException))
				throw new RuntimeException(e); //send it to the registered uncaught exception handler 
		}
	}
}
