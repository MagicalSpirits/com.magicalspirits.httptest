package com.magicalspirits.httptest.acceptor;

import java.net.Socket;

import lombok.Setter;

public class SocketRunnerImpl  implements SocketRunner
{
	@Setter(onMethod=@__(@Override))
	private Socket socket;
	
	@Override
	public void run()
	{ 
		// Step 0, begin http keep alive on this channel when its not active if we have time.
		// Step 1, first line RURI and protocol
		// Step 2, headers. Note: If I was developing a full http stack, 
		//  I would have to watch for a body part and either decode form data, or build an decoding inputstream around it.
		//  I'm only working with gets without bodies for this example for now.
		//  Also, this is the point where I would want to check access. Http Auth or otherwise. 
		//   Test the URI against some ACL and return 401 Unauthorized if http auth is requested, or 403 Forbidden if not allowed.
		//Content transfer chunked? Probably ignore for now.
		
		// Step 3, provide an outputstream in an appropriate encoding, and some way to respond with an http status code and headers.
		// Step 4, hand off request with outputstream to executor service to do the actual processing.
	}
}
