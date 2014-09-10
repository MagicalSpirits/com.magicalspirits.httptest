package com.magicalspirits.httptest.httpapplication;

import com.google.common.collect.ArrayListMultimap;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.magicalspirits.httptest.httpparser.HttpRuriData;

public interface ApplicationRunner extends SocketRunner 
{
	public void setHttpRuri(HttpRuriData httpRuri);

	public void setHeaders(ArrayListMultimap<String, String> headers);
}
