package com.magicalspirits.httptest.httpapplication;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.magicalspirits.httptest.ExecutorsModule;
import com.magicalspirits.httptest.acceptor.SocketRunner;
import com.magicalspirits.httptest.httpparser.HttpRuriData;

@Slf4j
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
	
	@Inject
	private Map<String, String> mimeTypeRegistry;
	
	@Inject
	@Named(ExecutorsModule.HTTP_SERVER_POOL)
	private ExecutorService serverPool;

	@Inject
	private Supplier<SocketRunner> socketRunnerSupplier;
	
	private OutputStream out;
	private PrintStream ps;
	
	@Override
	public void run() 
	{
		try
		{
			//NOTE: There are a lot of things we should do here with transfer encoding, 
			// content encoding, content transfer chunked. My results may not be strictly RFC compliant,
			// as I'm skipping a bunch of that work for speed of this demo.
	
			//4096 is generally a good size for data traversing the internet. IF this were local, we might want something bigger.
			out = new BufferedOutputStream(socket.getOutputStream(), 4096); 
			ps = new PrintStream(out, true);
			
			//some sanity checking
			if(!httpRuri.getRuriPath().startsWith("/") || httpRuri.getRuriPath().contains("/.."))
			{
				returnResponseCode(403, "Forbidden");
				finish();
				return;
			}
			
			if(!"GET".equalsIgnoreCase(httpRuri.getRequestType()))
			{
				//As noted previously, this is only for GET methods, and otherwise I would 
				// have to get creative with the line reading in the http parser.
				returnResponseCode(405, "Method Not Allowed");
				finish();
				return;
			}
			
			//Note: The root directory in a production environment would come dependency injection rather than a classpath resource.
			// This is good enough for this demo.
			
			File file = new File(ServeHttpFile.class.getClassLoader().getResource("wwwroot").getFile() + httpRuri.getRuriPath());
			//Note: Default behavior of an empty url would be to have it try an index.html. That's not in this demo, but wouldn't be
			//hard to check for here and add.
			
			if(!file.exists() || !file.isFile())
			{
				returnResponseCode(404, "Not Found");
				finish();
				return;
			}
		
			writeResult(200, "OK", file);
			finish();
		}
		catch(IOException ioe)
		{
			try
			{
				//try to send a 500
				returnResponseCode(500, "Internal Server Error");
				finish();
			}
			catch(Exception e)
			{
				log.warn("Unable to send 500 after another error", e);
			}
			
			try 
			{
				socket.close();
			} 
			catch (IOException e1) 
			{
				log.warn("Unable to close socket", e1);
			}
			throw new RuntimeException(ioe);
		}
	}
	
	private void writeResult(int responseCode, String httpMessage, File file) throws IOException 
	{
		ps.println(httpRuri.getVersion() + " " + responseCode + " " + httpMessage);
		ps.println(HttpHeaders.CONTENT_LENGTH + ": " + file.length());
		
		//bin is a great default.
		String mimeType = mimeTypeRegistry.get("");
		String ext = Files.getFileExtension(file.getName());
		if(!Strings.isNullOrEmpty(ext) && mimeTypeRegistry.containsKey(ext))
			mimeType = mimeTypeRegistry.get(ext);
		
		ps.println(HttpHeaders.CONTENT_TYPE + ": " + mimeType);
		
		ps.println();
		ps.flush();
		
		try(FileInputStream fis = new FileInputStream(file))
		{
			ByteStreams.copy(fis, out);
		}
		out.flush();
	}

	private void finish()
	{
		try
		{
			ps.flush();
			out.flush();
		}
		catch(IOException e)
		{
			try 
			{
				socket.close();
			} 
			catch (IOException e1) 
			{
				log.warn("Unable to close socket", e1);
			}
			throw new RuntimeException(e);			
		}
		if(httpRuri.getVersion().equalsIgnoreCase("HTTP/1.1") && !socket.isClosed())
		{
			//reschedule this 
			SocketRunner sr = socketRunnerSupplier.get();
			sr.setBufferedReader(bufferedReader);
			sr.setSocket(socket);
			serverPool.execute(sr);
		}
		else
		{
			try
			{
				socket.close();
			} 
			catch (IOException e1) 
			{
				log.warn("Unable to close socket", e1);
			}
		}
	}
	
	private void returnResponseCode(int responseCode, String httpMessage)
	{
		if(socket.isClosed())
			return;
		ps.println(httpRuri.getVersion() + " " + responseCode + " " + httpMessage);
		ps.println(HttpHeaders.CONTENT_LENGTH + ": 0");
		ps.println();
		ps.flush();
	}
}
