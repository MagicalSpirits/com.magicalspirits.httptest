package com.magicalspirits.httptest;

import java.lang.Thread.UncaughtExceptionHandler;

import com.codahale.metrics.annotation.Metered;
import com.google.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class LoggingUncaughtExceptionHandler implements UncaughtExceptionHandler 
{
	@Override
	@Metered
	public void uncaughtException(Thread t, Throwable e) 
	{
		log.error("Thread {} encountered uncaught exception", t, e);
	}

}
