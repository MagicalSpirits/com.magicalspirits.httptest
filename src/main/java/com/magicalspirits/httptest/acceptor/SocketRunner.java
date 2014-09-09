package com.magicalspirits.httptest.acceptor;

import java.net.Socket;

public interface SocketRunner extends Runnable 
{
	public void setSocket(Socket socket);
}
