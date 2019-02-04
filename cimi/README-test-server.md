SSCLJ Test Server
=================

This repository creates a small jar file that allows an SSCLJ
test server to be started from Java (in addition to the core server
jar file).

The standard `ssclj.jar` file (and transitive dependencies) must be on
the classpath so that all the resource definitions can be found and
loaded into the test server.  The test server should not need any
other dependencies.

The following dependencies should be added to the POM file of you module.

	<dependency>
		<groupId>com.sixsq.slipstream</groupId>
		<artifactId>SlipStreamCljResourcesTestServer-jar</artifactId>
		<scope>test</scope>
		<version>${project.version}</version>
	</dependency>


To start the server, import the class:

    com.sixsq.slipstream.ssclj.app.CIMITestServer

You can then call the **static** methods `start` and `stop` to start
and stop the server, respectively. The following services will be
running on the ports:

 - 12001: zookeeper
 - 12002: elasticsearch
 - 12003: ssclj

You can test that the SSCLJ server is responding correctly by
requesting the cloud entry point:

    curl http://localhost:12003/api/cloud-entry-point

It should respond with the typical cloud entry point document. Check
that all the resources have been loaded into the server.

Elasticsearch by default refreshes its indices each 200ms.  To force
refresh of all the indices (for example from unit tests), call the
**static** `refresh` method.  E.g.:

    Vm vm = new Vm(instanceID, "cloud", "Running", "user", true);
    // add document
    VirtualMachineHandler.handleVM(vm);
    SscljTestServer.refresh();

    // get document and assert
    VirtualMachine vmResource = VirtualMachineHandler.fetchVirtualMachine("cloud", instanceID);
    Assert.assertNotEquals(null, vmResource);
    Assert.assertEquals(instanceID, vmResource.getInstanceID());

    // remove document
    VirtualMachineHandler.removeVM(vm);
    SscljTestServer.refresh();

    // get and assert no longer exists
    vmResource = VirtualMachineHandler.fetchVirtualMachine("cloud", instanceID);
    Assert.assertEquals(null, vmResource);
