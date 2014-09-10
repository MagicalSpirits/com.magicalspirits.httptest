com.magicalspirits.httptest
============================

This is a sample http server (GET requests only) for demonstration purposes.

To run this http server, execute com.magicalspirits.httptest.launcher.Main

com.magicalspirits.httptest.launcher.Service is a jsvc capable service, if you want to run this http server as a service.

This http server will serve files out of a resource called wwwroot. Typically, if you build this in eclipse, you will find the source files in src/main/resources/wwwroot

This server will listen on http://localhost:8080

Additionally to serving of static content, this server has two additional urls bound:
* http://localhost:8080/monitoring shows bound codahale health check services. Presently, only the jvm deadlock detector is bound.
* http://localhost:8080/metrics shows bound metrics. This application is providing many of these, and the JVM is also providing many more. You can use this to see what this http server is doing on the inside.

A couple of pieces of sample content are included for testing:
* http://localhost:8080/testfile1.txt a sample text file
* http://localhost:8080/simple.html a sample html file
* http://localhost:8080/binarydata.bin a sample binary file

These files will each download with an appropriate mime type.

High level system design:
---------------------------------

This server represents a pipeline of events being executed asynchronously. This event chain typically looks like:
Acceptor -> Parser -> Application

Or in more detail:
* Acceptor gets a socket from the server socket ->
* HttpRuriParser parses the initial line of the request ->
* HttpHeaderParser parses the http headers from the request ->
* ServeHttpFile produces a result either file based, or dynamic content, and sends the response back to the client.

This service has two high level executors (thread pools). There is a system one handling the acceptor and parser portions, and a application pool responsible for handling the application aspects and sending the data back to the client.

High level goals of this project:
---------------------------------
* Provide a working file based http server.
* Use dependency injection (Guice) to enable portions to be swapped in potential different configurations (such as testing).
* Provide metrics and monitoring (Codahale) so that this service could be used in a production capacity.
* Provide high throughput request processing. On my macbook, I can execute 1000 requests in less than a second.
* Provide robust unit testing.
* Provide automated system level testing.
* Provide a minimum viable product http server.


Package structure:
---------------------------------
* com.magicalspirits.httptest.acceptor: Contains the components that handle receiving the server socket, accepting connections from it, and handing them off to the next layer
* com.magicalspirits.httptest.httpapplication: Contains the components that process the completely parsed http request and provide a response to the client.
* com.magicalspirits.httptest.httpparser: Contains the components that parse the http request line and headers, and hands them off to the application layer.
* com.magicalspirits.httptest.launcher: Contains the components that start the application such as Main, Service, guice modules, and the default uncaught exception handler.
* com.magicalspirits.httptest.metricsmonitoring: Contains the Guice binding to Codahale for metrics and monitoring.
* com.magicalspirits.httptest.3rdparty: Contains a component presently not in the production release of Codahale that allows me to instrument an ExecutorService for metrics and monitoring. This component is from the next codahale release and isn't mine or written by me.
  
All source code contained in this package, except for that in 3rd party, and a couple of small snippits that are noted internally have been written by me. 

A couple pieces of code I've borrowed from an open source project of mine https://github.com/TechBlueprints/com.techblueprints.microservices.restnow

This project is a real HTTP JaxRS 2.0 container that I have been working on. If you want http JaxRS2.0 REST micro/macro services you should look there rather than here.


Apache Software License 2.0:
-----------------------------------------------------------------------------
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

