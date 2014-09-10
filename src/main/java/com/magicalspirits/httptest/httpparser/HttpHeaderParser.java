package com.magicalspirits.httptest.httpparser;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import lombok.Setter;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.magicalspirits.httptest.httpapplication.ApplicationRunner;

public class HttpHeaderParser implements SocketRunner
{
	@Inject
	private ExecutorService defaultPool;
	
	@Setter(onMethod=@__(@Override))
	private Socket socket;

	@Setter(onMethod=@__(@Override))
	private BufferedReader bufferedReader;
	
	@Setter
	private HttpRuriData httpRuri;

	//rfc2616 says that commas split header fields into multiple values
	private Splitter commaSplitter = Splitter.on(",");
	
	@Inject
	private Supplier<ApplicationRunner> applicationRunnerSuppler;
	
	@Override
	public void run() 
	{
		ArrayListMultimap<String, String> httpHeaders = ArrayListMultimap.create();
		try
		{
			for(String header = bufferedReader.readLine(); Strings.isNullOrEmpty(header); header = bufferedReader.readLine())
			{
				parseHeader(header, httpHeaders);
			}
		}
		catch(IOException ioe)
		{
			throw new RuntimeException(ioe);
		}
		
		ApplicationRunner runner = applicationRunnerSuppler.get();
		runner.setSocket(socket);
		runner.setBufferedReader(bufferedReader);
		runner.setHttpRuri(httpRuri);
		runner.setHeaders(httpHeaders);
		defaultPool.execute(runner);
	}
	
	public void parseHeader(String header, Multimap<String, String> httpHeaders)
	{
		int indexOfColon = header.indexOf(":");
		if(indexOfColon == -1)
			indexOfColon = header.length() - 1;
		//rfc2616 seems to indicate I can safely trim the keys and values.
		String key = header.substring(0, indexOfColon).trim();
		String value;			
		if(indexOfColon + 1 < header.length())
			value = header.substring(indexOfColon + 1).trim();
		else
			value = "";
		httpHeaders.get(key).addAll(commaSplitter.splitToList(value));
	}
}
