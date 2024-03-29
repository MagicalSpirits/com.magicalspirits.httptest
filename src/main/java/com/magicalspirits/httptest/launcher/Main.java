package com.magicalspirits.httptest.launcher;

import java.util.Scanner;

import lombok.Cleanup;

import com.google.common.base.Strings;

public class Main 
{
	public static void main(String[] args) throws InterruptedException
	{
		if(Strings.isNullOrEmpty(System.getProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY)))
			System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");
		
		Service main = new Service();
		main.init(args);
		main.start();
		
		@Cleanup Scanner s = new Scanner(System.in);
		while(true)
		{
			System.out.println("Type exit and press enter to end process");
			if(s.hasNext() && "exit".equalsIgnoreCase(s.next()))
			{
				System.out.println("Shutting down.");
				main.stop();
				System.out.println("Shutdown complete.");
				break;
			}
		}
	}
}
