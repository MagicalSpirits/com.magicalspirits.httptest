package com.magicalspirits.httptest.httpapplication;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	private ExecutorService defaultPool;

	@Inject
	private Supplier<SocketRunner> socketRunnerSupplier;
	
	@Inject 
	private ObjectMapper mapper; //for metrics and monitoring.
	
	@Inject
	private HealthCheckRegistry healthRegistry;
	
	@Inject 
	private MetricRegistry metricRegistry;
	
	private OutputStream out;
	private PrintStream ps;
	
	@Override
	@Metered(name="run.meter")
	@Timed(name="run.timed")
	@ExceptionMetered(name="run.exceptionmeter")
	public void run() 
	{
		try
		{
			//NOTE: There are a lot of things we should do here with transfer encoding, 
			// content encoding, content transfer chunked. 
			// I'm going to minimum viable product for this demo, so I'm skipping thse areas.
	
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
			
			//Note: Here we would probably use a list of some path matching to the actual class that produces the result, however
			// since I'm only adding metrics and monitoring, I'm not going to be that dynamic about it.
			
			if("/metrics".equals(httpRuri.getRuriPath()))
			{
				writeMetrics();
				finish();
				return;
			}
			if("/monitoring".equals(httpRuri.getRuriPath()))
			{
				writeMonitoring();
				finish();
				return;
			}
			
			//otherwise, look for a file
			
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
	
	private void writeMetrics() throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		//I have to wrap this in a byte array outputstream since I dont know the size, and I
		// dont support chunked encoding.
		mapper.writerWithDefaultPrettyPrinter().writeValue(baos, metricRegistry);

		writeResult(200, "OK", "txt", baos.size(), new ByteArrayInputStream(baos.toByteArray()));
	}
	
	private void writeMonitoring() throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		//I have to wrap this in a byte array outputstream since I dont know the size, and I
		// dont support chunked encoding.
		mapper.writerWithDefaultPrettyPrinter().writeValue(baos, healthRegistry.runHealthChecks(defaultPool));

		writeResult(200, "OK", "txt", baos.size(), new ByteArrayInputStream(baos.toByteArray()));
	}

	private void writeResult(int responseCode, String httpMessage, File file) throws IOException 
	{
		String ext = Files.getFileExtension(file.getName());
		try(FileInputStream fis = new FileInputStream(file))
		{
			writeResult(responseCode, httpMessage, ext, file.length(), fis);
		}
	}

	private void writeResult(int responseCode, String httpMessage, String ext, long length, InputStream in) throws IOException 
	{
		writeResultHeader(responseCode, httpMessage, ext, length);
		ByteStreams.copy(in, out);
		out.flush();
	}
	
	private void writeResultHeader(int responseCode, String httpMessage, String ext, long length) throws IOException 
	{
		ps.println(httpRuri.getVersion() + " " + responseCode + " " + httpMessage);
		ps.println(HttpHeaders.CONTENT_LENGTH + ": " + length);
		
		//"" is a great default.
		String mimeType = mimeTypeRegistry.get("");
		if(!Strings.isNullOrEmpty(ext) && mimeTypeRegistry.containsKey(ext))
			mimeType = mimeTypeRegistry.get(ext);
		
		ps.println(HttpHeaders.CONTENT_TYPE + ": " + mimeType);
		
		ps.println();
		ps.flush();
	}

	@Metered(name="finish.meter")
	@Timed(name="finish.timed")
	@ExceptionMetered(name="finish.exceptionmeter")
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
			boolean closeRequired = false;
			
			List<String> closed = headers.get(HttpHeaders.CONNECTION);
			for(String closeValue : closed)
			{
				if("closed".equalsIgnoreCase(closeValue))
				{
					closeRequired = true;
					break;
				}
			}
			if(closeRequired)
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
			else
			{
				//reschedule this 
				SocketRunner sr = socketRunnerSupplier.get();
				sr.setBufferedReader(bufferedReader);
				sr.setSocket(socket);
				serverPool.execute(sr);
			}
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
	
	@Metered(name="responsecode.meter")
	@Timed(name="responsecode.timed")
	@ExceptionMetered(name="responsecode.exceptionmeter")
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
