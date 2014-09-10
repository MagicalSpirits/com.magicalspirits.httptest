package com.magicalspirits.httptest.acceptor;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.magicalspirits.httptest.ExecutorsModule;

public class AcceptorService 
{
	@Inject
	private List<ServerSocketAcceptor> acceptors;

	@Inject
	@Named(ExecutorsModule.HTTP_SERVER_POOL)
	private ExecutorService serverPool;
	
	@PostConstruct
	public void start()
	{
		for(ServerSocketAcceptor ssa : acceptors)
			serverPool.execute(ssa);
	}
}
