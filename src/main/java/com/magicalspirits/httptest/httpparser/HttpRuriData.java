package com.magicalspirits.httptest.httpparser;

import lombok.Value;

@Value
public class HttpRuriData 
{
	private String requestType;
	private String ruriPath;
	private String version;
}
