package com.magicalspirits.httptest.httpparser;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.magicalspirits.httptest.launcher.ExecutorsModule;

@Slf4j
public class HttpRuriParser implements SocketRunner
{	
	
	@Setter(onMethod=@__(@Override))
	private Socket socket;

	@Setter(onMethod=@__(@Override))
	private BufferedReader bufferedReader;
	
	@Inject
	@Named(ExecutorsModule.HTTP_SERVER_POOL)
	private ExecutorService serverPool;
	
	private static final Splitter spaceSplitter = Splitter.on(' ');
	
	@Inject
	private Supplier<HttpHeaderParser> httpHeaderParserSupplier;
	
	private static final int MAX_EMPTY_LINES_BEFORE_RURI = 10;
	
	@Override
	@Metered(name="run.meter")
	@Timed(name="run.timed")
	@ExceptionMetered(name="run.exceptionmeter")
	public void run() 
	{
		try
		{
			String rUriLine = "";
			try 
			{
				//some clients are sloppy, especially with http 1.1 handling.
				//We'll give them a little leeway with empty lines at the beginning.
				for(int i = 0; i < MAX_EMPTY_LINES_BEFORE_RURI && "".equals(rUriLine); i++)
				{
					rUriLine = bufferedReader.readLine();
					if(rUriLine == null)
					{
						log.warn("Connection closed on {} before initial request", socket);
						return;
					}
				}
			} 
			catch(SocketTimeoutException ste)
			{
				timeout();
				socket.close();
				return;
			}
			catch (IOException e) 
			{
				log.warn("Unable to read ruri line for {}", socket, e);
				socket.close(); //Unable to read. is it http? who knows, just close it.
				return;
			}
			if("".equals(rUriLine))
			{
				log.info("Initial line empty for {} request will not be processed", socket);
				socket.close(); //not http, just close it.
				return;
			}
			//from RFC 2616:
			// The Request-Line begins with a method token, followed by the Request-URI and the protocol version,
			// and ending with CRLF. The elements are separated by SP characters. No CR or LF is allowed except in the final CRLF sequence.
			
			List<String> header = spaceSplitter.splitToList(rUriLine);

			// rfc3986 declares request line has a specific format of 3 entries. Request type, uri, and version.
			if(header.size() != 3)
			{
				log.info("Initial line incorrect length {} for {} request will not be processed", header, socket);
				socket.close(); //not http, just close it.
			}

			//This is http.

			//use java built in url decoder to decode %xx and +
			HttpRuriData data = new HttpRuriData(
					header.get(0), URLDecoder.decode(
							header.get(1), Charsets.ISO_8859_1.name()), 
					header.get(2));
			
			HttpHeaderParser parser =  httpHeaderParserSupplier.get();
			parser.setBufferedReader(bufferedReader);
			parser.setSocket(socket);
			parser.setHttpRuri(data);
			serverPool.execute(parser);
		}
		catch(IOException e)
		{
			try
			{
				socket.close();
			}
			catch(IOException e2)
			{
				log.debug("Unable to close socket inside failure case", e2);
			}
			throw new RuntimeException(e); // unable to close socket.
		}
	}

	@Metered
	protected void timeout()
	{
		//Spearate method to take advantage of a meter on this method
		log.warn("Timeout reading ruri line for {}", socket);
	}
}
