package com.magicalspirits.httptest.acceptor;

import java.io.BufferedReader;
import java.net.Socket;

public interface SocketRunner extends Runnable 
{
	public void setSocket(Socket socket);
	
	//Note: this wouldn't work so well if I were handling submissions (PUT/POST) requests, as I would have
	// to either move the source inputstream back to the end of the last line, and that wont be possible with
	// all underlying socket streams, however, since we are only handling get requests. I will cheat and pass this around.
	public void setBufferedReader(BufferedReader br);
}
