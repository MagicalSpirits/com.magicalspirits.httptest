package com.magicalspirits.httptest.httpapplication;

import java.io.BufferedReader;
import java.net.Socket;

import lombok.Setter;

import com.google.common.collect.ArrayListMultimap;
import com.magicalspirits.httptest.httpparser.HttpRuriData;

public class ServeHttpFile implements ApplicationRunner 
{	
	@Setter(onMethod=@__(@Override))
	private Socket socket;

	@Setter(onMethod=@__(@Override))
	private BufferedReader bufferedReader;
	
	@Setter(onMethod=@__(@Override))
	private HttpRuriData httpRuri;

	@Setter(onMethod=@__(@Override))
	private ArrayListMultimap<String, String> headers;
	
	@Override
	public void run() 
	{
		

	}
}
