/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the MuleSource MPL
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport.sftp;

import static org.mule.context.notification.EndpointMessageNotification.MESSAGE_DISPATCHED;
import static org.mule.context.notification.EndpointMessageNotification.MESSAGE_SENT;

import java.beans.ExceptionListener;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.mule.api.MuleEventContext;
import org.mule.api.MuleException;
import org.mule.api.context.notification.EndpointMessageNotificationListener;
import org.mule.api.context.notification.ServerNotification;
import org.mule.api.endpoint.EndpointBuilder;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.context.notification.EndpointMessageNotification;
import org.mule.module.client.MuleClient;
import org.mule.tck.FunctionalTestCase;
import org.mule.tck.functional.EventCallback;
import org.mule.transport.sftp.util.ValueHolder;
import org.mule.util.StringMessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

import edu.emory.mathcs.backport.java.util.concurrent.CountDownLatch;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import edu.emory.mathcs.backport.java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Lennart Häggkvist, Magnus Larsson
 *         Date: Jun 8, 2009
 */
public abstract class AbstractSftpTestCase extends FunctionalTestCase
{
	
    protected static final String FILE_NAME = "file.txt";

	protected final Logger logger = LoggerFactory.getLogger(AbstractSftpTestCase.class);

	/** Deletes all files in the directory, useful when testing to ensure that no files are in the way... */
//	protected void cleanupRemoteFtpDirectory(MuleClient muleClient, String endpointName) throws IOException
//	{
//		SftpClient sftpClient = getSftpClient(muleClient, endpointName);
//
//		EndpointURI endpointURI = getUriByEndpointName(muleClient, endpointName);
//		sftpClient.changeWorkingDirectory(sftpClient.getAbsolutePath(endpointURI.getPath()));
//
//		String[] files = sftpClient.listFiles();
//		for (String file : files)
//		{
//			sftpClient.deleteFile(file);
//		}
//	}


	/**
	 * Deletes a directory with all its files and sub-directories. The reason it do a "chmod 700" before the delete is that some tests
	 * changes the permission, and thus we have to restore the right to delete it...
	 *
	 * @param muleClient
	 * @param endpointName
	 * @param relativePath
	 * @throws IOException
	 */
	protected void recursiveDelete(MuleClient muleClient, SftpClient sftpClient, String endpointName, String relativePath) throws IOException
	{
		EndpointURI endpointURI = getUriByEndpointName(muleClient, endpointName);
		String path = endpointURI.getPath() + relativePath;

		try
		{
			// Ensure that we can delete the current directory and the below directories (if write is not permitted then delete is either)
			sftpClient.chmod(path, 00700);

			sftpClient.changeWorkingDirectory(sftpClient.getAbsolutePath(path));

			// Delete all sub-directories
			String[] directories = sftpClient.listDirectories();
			for (String directory : directories)
			{
				recursiveDelete(muleClient, sftpClient, endpointName, relativePath + "/" + directory);
			}

            // Needs to change the directory back after the recursiveDelete
            sftpClient.changeWorkingDirectory(sftpClient.getAbsolutePath(path));

			// Delete all files
			String[] files = sftpClient.listFiles();
			for (String file : files)
			{
				sftpClient.deleteFile(file);
			}

			// Delete the directory
			try
			{
				sftpClient.deleteDirectory(path);
			} catch (Exception e)
			{
				if (logger.isDebugEnabled()) logger.debug("Failed delete directory " + path, e);
			}

		} catch (Exception e)
		{
			if (logger.isDebugEnabled()) logger.debug("Failed to recursivly delete directory " + path, e);
		}
	}

	/** Creates the <i>directoryName</i> under the endpoint path */
	protected void createRemoteDirectory(MuleClient muleClient, String endpointName, String directoryName) throws IOException
	{
		SftpClient sftpClient = getSftpClient(muleClient, endpointName);

        try {
            EndpointURI endpointURI = getUriByEndpointName(muleClient, endpointName);
            sftpClient.changeWorkingDirectory(sftpClient.getAbsolutePath(endpointURI.getPath()));

            try
            {
                sftpClient.mkdir(directoryName);
            } catch (IOException e)
            {
                e.printStackTrace();
                // Expected if the directory didnt exist
            }

            try
            {
                sftpClient.changeWorkingDirectory(endpointURI.getPath() + "/" + directoryName);
            } catch (IOException e)
            {
                fail("The directory should have been created");
            }
        } finally {
            sftpClient.disconnect();
        }
    }

	protected EndpointURI getUriByEndpointName(MuleClient muleClient, String endpointName) throws IOException
	{
		ImmutableEndpoint endpoint = getImmutableEndpoint(muleClient, endpointName);
		return endpoint.getEndpointURI();
	}

	/**
	 * @param muleClient
	 * @param endpointName
	 * @return the endpoint address in the form 'sftp://user:password@host/path'
	 */
	protected String getAddressByEndpoint(MuleClient muleClient, String endpointName)
	{
		ImmutableEndpoint endpoint = (ImmutableEndpoint) muleClient.getProperty(endpointName);
		EndpointURI endpointURI = endpoint.getEndpointURI();

		return "sftp://" + endpointURI.getUser() + ":" + endpointURI.getPassword() + "@" + endpointURI.getHost() + endpointURI.getPath();
	}

	protected String getPathByEndpoint(MuleClient muleClient, SftpClient sftpClient, String endpointName)
	{
		ImmutableEndpoint endpoint = (ImmutableEndpoint) muleClient.getProperty(endpointName);
		EndpointURI endpointURI = endpoint.getEndpointURI();

		return sftpClient.getAbsolutePath(endpointURI.getPath());
	}

	/**
	 * Returns a SftpClient that is logged in to the sftp server that the endpoint is configured against.
	 *
	 * @param muleClient
	 * @param endpointName
	 * @return
	 * @throws IOException
	 */
	protected SftpClient getSftpClient(MuleClient muleClient, String endpointName)
		throws IOException
	{
		ImmutableEndpoint endpoint = getImmutableEndpoint(muleClient, endpointName);
		EndpointURI endpointURI = endpoint.getEndpointURI();
		SftpClient sftpClient = new SftpClient(endpointURI.getHost());

		SftpConnector sftpConnector = (SftpConnector) endpoint.getConnector();

		if (sftpConnector.getIdentityFile() != null)
		{
			try
			{
				sftpClient.login(endpointURI.getUser(), sftpConnector.getIdentityFile(), sftpConnector.getPassphrase());
			} catch(Exception e) {
				fail("Login failed: " + e);
			}
		} else
		{
			try
			{
				sftpClient.login(endpointURI.getUser(), endpointURI.getPassword());
			} catch(Exception e) {
				fail("Login failed: " + e);
			}
		}
		return sftpClient;
	}

	/** Checks if the file exists on the server */
	protected boolean verifyFileExists(SftpClient sftpClient, EndpointURI endpointURI, String file) throws IOException
	{
		return verifyFileExists(sftpClient, endpointURI.getPath(), file);
	}

	protected boolean verifyFileExists(SftpClient sftpClient, String path, String file) throws IOException
	{
		sftpClient.changeWorkingDirectory(sftpClient.getAbsolutePath(path));
		String[] files = sftpClient.listFiles();

		for (String remoteFile : files)
		{
			if (file.equals(remoteFile))
			{
				return true;
			}
		}
		return false;
	}

	/** Base method for executing tests... */
	protected void executeBaseTest(String inputEndpointName, String sendUrl, String filename, final int size, String receivingTestComponentName, long timeout) throws Exception
	{
		executeBaseTest(inputEndpointName, sendUrl, filename, size, receivingTestComponentName, timeout, null);
	}

	/** Base method for executing tests... */
	protected void executeBaseTest(String inputEndpointName, String sendUrl, String filename, final int size, String receivingTestComponentName, long timeout, String expectedFailingConnector) throws Exception
	{
		MuleClient client = new MuleClient();

		// Do some cleaning so that the endpoint doesn't have any other files
        // We don't need to do this anymore since we are deleting and then creating the directory for each test
		// cleanupRemoteFtpDirectory(client, inputEndpointName);

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicInteger loopCount = new AtomicInteger(0);
		final AtomicInteger totalReceivedSize = new AtomicInteger(0);

		// Random byte that we want to send a lot of
		final int testByte = 42;

		EventCallback callback = new EventCallback()
		{
			public void eventReceived(MuleEventContext context, Object component)
				throws Exception
			{
				if (logger.isInfoEnabled()) logger.info("called " + loopCount.incrementAndGet() + " times");

				InputStream sftpInputStream = (InputStream) context.getMessage().getPayload();
				BufferedInputStream bif = new BufferedInputStream(sftpInputStream);
				byte[] buffer = new byte[1024 * 4];

				try
				{
					int n;
					while (-1 != (n = bif.read(buffer)))
					{
						totalReceivedSize.addAndGet(n);

						// Simple check to verify the data...
						for (byte b : buffer)
						{
							if (b != testByte)
							{
								fail("Incorrect received byte (was '" + b + "', excepted '" + testByte + "'");
							}
						}
					}
				} finally
				{
					bif.close();
				}


				latch.countDown();
			}
		};
		getFunctionalTestComponent(receivingTestComponentName).setEventCallback(callback);


		final ValueHolder<Exception> exceptionHolder = new ValueHolder<Exception>();
		if (expectedFailingConnector != null) {
			// Register an exception-listener on the connector that expects to fail and count down the latch after saving the throwed exception
			muleContext.getRegistry().lookupConnector(expectedFailingConnector).setExceptionListener(new ExceptionListener() {
				public void exceptionThrown(Exception e) {
					exceptionHolder.value = e;
					latch.countDown();
				}
			});
		}


		// InputStream that generates the data without using a file
		InputStream os = new InputStream()
		{
			int totSize = 0;

			public int read() throws IOException
			{
				totSize++;
				if (totSize <= size)
				{
					return testByte;
				} else
				{
					return -1;
				}
			}
		};

		HashMap<String, String> props = new HashMap<String, String>(1);
		props.put(SftpConnector.PROPERTY_FILENAME, filename);

		if (logger.isInfoEnabled()) logger.info(StringMessageUtils.getBoilerPlate("Note! If this test fails due to timeout please add '-Dmule.test.timeoutSecs=XX' to the mvn command!"));

		executeBaseAssertionsBeforeCall();

		// Send the content using stream
		client.dispatch(sendUrl, os, props);

		boolean workDone = latch.await(timeout, TimeUnit.MILLISECONDS);

		assertTrue("Test timed out. It took more than " + timeout + " milliseconds. If this error occurs the test probably needs a longer time out (on your computer/network)", workDone);

		// Rethrow any exception that we have caught in an exception-listener
    	if (exceptionHolder.value != null) {
    		throw exceptionHolder.value;
    	}
		executeBaseAssertionsAfterCall(size, totalReceivedSize.intValue());
	}

	/**
	 * To be overridden by the test-classes if required
	 */
	protected void executeBaseAssertionsBeforeCall() {
	}

	/**
	 * To be overridden by the test-classes if required
	 */
	protected void executeBaseAssertionsAfterCall(int sendSize, int receivedSize) {

		// Make sure that the file we received had the same size as the one we sent
		if (logger.isInfoEnabled()) {
			logger.info("Sent size: " + sendSize);
			logger.info("Received size: " + receivedSize);
		}

		assertEquals("The received file should have the same size as the sent file", sendSize, receivedSize);
	}

	private ImmutableEndpoint getImmutableEndpoint(MuleClient muleClient,
													 String endpointName) throws IOException
	{
		ImmutableEndpoint endpoint = null;

		Object o = muleClient.getProperty(endpointName);
		if (o instanceof ImmutableEndpoint)
		{
			// For Inbound and Outbound Endpoints
			endpoint = (ImmutableEndpoint) o;

		} else if (o instanceof EndpointBuilder)
		{
			// For Endpoint-references
			EndpointBuilder eb = (EndpointBuilder) o;
			try
			{
				endpoint = eb.buildInboundEndpoint();
			} catch (Exception e)
			{
				throw new IOException(e.getMessage());
			}
		}
		return endpoint;
	}

	protected void remoteChmod(MuleClient muleClient, SftpClient sftpClient, String endpointName, int permissions) throws SftpException
	{
        ChannelSftp channelSftp = sftpClient.getChannelSftp();

		ImmutableEndpoint endpoint = (ImmutableEndpoint) muleClient.getProperty(endpointName);
		EndpointURI endpointURI = endpoint.getEndpointURI();

		// RW - so that we can do initial cleanup
		channelSftp.chmod(permissions, sftpClient.getAbsolutePath(endpointURI.getPath()));
	}

    /**
     * Ensures that the directory exists and is writable by deleting the directory and then recreate it.
     *
     * @param endpointName
     * @throws org.mule.api.MuleException
     * @throws java.io.IOException
     * @throws com.jcraft.jsch.SftpException
     */
    protected void initEndpointDirectory(String endpointName) throws MuleException, IOException, SftpException
    {
        MuleClient muleClient = new MuleClient();
        SftpClient sftpClient = getSftpClient(muleClient, endpointName);
        try {
            ChannelSftp channelSftp = sftpClient.getChannelSftp();
            try
            {
                recursiveDelete(muleClient, sftpClient, endpointName, "");
            } catch (IOException e)
            {
    			if (logger.isErrorEnabled()) logger.error("Failed to recursivly delete endpoint " + endpointName, e);
            }

            String path = getPathByEndpoint(muleClient, sftpClient, endpointName);
            channelSftp.mkdir(path);
        } finally {
            sftpClient.disconnect();
            if (logger.isDebugEnabled()) logger.debug("Done init endpoint directory: " + endpointName);
        }
    }

	/**
	 * Helper method for initiating a test and wait for the test to complete.
	 * The method sends a file to an inbound endpoint and waits for a dispatch event on a outbound endpoint,
	 * i.e. that the file has been consumed by the inbound endpoint and that the content of the file has been sent to the outgoing endpoint.
	 * 
	 * @param p where inboundEndpoint and outboundEndpoint are mandatory, @see DispatchParameters for details.
	 */
	protected void dispatchAndWaitForDelivery(final DispatchParameters p)
    {
    	// Declare countdown latch and listener
		final CountDownLatch latch = new CountDownLatch(1);
		EndpointMessageNotificationListener listener = null;
		MuleClient muleClient = p.getMuleClient();
		boolean localMuleClient = muleClient == null;

		try {
			// First create a local muleClient instance if not supplied
			if (localMuleClient) muleClient = new MuleClient();
			
			// Next create a listener that listens for dispatch events on the outbound endpoint
			listener = new EndpointMessageNotificationListener() {
				public void onNotification(ServerNotification notification) {

					// Only care about EndpointMessageNotification
					if (notification instanceof EndpointMessageNotification) {
						EndpointMessageNotification endpointNotification = (EndpointMessageNotification)notification;

						// Extract action and name of the endpoint
						int    action   = endpointNotification.getAction();
						String endpoint = endpointNotification.getEndpoint().getName();

						System.err.println("### action: " + endpointNotification.getActionName() + ", endpoint: " + endpoint);
						
						// If it is a dispatch event on our outbound endpoint then countdown the latch.
						if ((action == MESSAGE_DISPATCHED || action == MESSAGE_SENT) && endpoint.equals(p.getOutboundEndpoint())) {
							if (logger.isDebugEnabled()) logger.debug("Expected notification received on " + p.getOutboundEndpoint() + " (action: " + action + "), time to countdown the latch");
							latch.countDown();
						}
					}
				}
			};
			
			// Now register the listener
			muleContext.getNotificationManager().addListener(listener);

			// Initiate the test by sending a file to the SFTP server, which the inbound-endpoint then can pick up

			// Prepare message headers, set filename-header and if supplied any headers supplied in the call.
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("filename", p.getFilename());
			if (p.getHeaders() != null) {
				headers.putAll(p.getHeaders());
			}

			// Setup connect string and perform the actual dispatch
			String connectString = (p.getSftpConnector() == null) ? "" : "?connector=" + p.getSftpConnector();
			muleClient.dispatch(getAddressByEndpoint(muleClient, p.getInboundEndpoint()) + connectString, TEST_MESSAGE, headers);
			
			// Wait for the delivery to occur...
			if (logger.isDebugEnabled()) logger.debug("Waiting for file to be delivered to the endpoint...");
			boolean workDone = latch.await(p.getTimeout(), TimeUnit.MILLISECONDS);			
			if (logger.isDebugEnabled()) logger.debug((workDone) ? "File delivered, continue..." : "No file delivered, timeout occurred!");

			// Raise a fault if the test timed out
			assertTrue("Test timed out. It took more than " + p.getTimeout() + " milliseconds. If this error occurs the test probably needs a longer time out (on your computer/network)", workDone);

		} catch (Exception e) {
			e.printStackTrace();
			fail("An unexpected error occurred: " + e.getMessage());

		} finally {
			// Dispose muleClient if created locally
			if (localMuleClient) muleClient.dispose();
			
			// Always remove the listener if created
			if (listener != null) muleContext.getNotificationManager().removeListener(listener);			
		}
    }

    /**
     * Helper method for initiating a test and wait for an exception to be catched by the sftp-connector.
     * 
	 * @param p where sftpConnector and inboundEndpoint are mandatory, @see DispatchParameters for details.
     * @return
     */
    protected Exception dispatchAndWaitForException(final DispatchParameters p, String expectedFailingConnector)
    {
    	// Declare countdown latch and listener
		final CountDownLatch latch = new CountDownLatch(1);
		ExceptionListener listener = null;
		MuleClient muleClient = p.getMuleClient();
		boolean localMuleClient = muleClient == null;
		ExceptionListener currentExceptionListener = null;
		final ValueHolder<Exception> exceptionHolder = new ValueHolder<Exception>();

		try {
			// First create a local muleClient instance if not supplied
			if (localMuleClient) muleClient = new MuleClient();
			
			// Next create a listener that listens for exception on the sftp-connector
			listener = new ExceptionListener() {
				public void exceptionThrown(Exception e) {
					exceptionHolder.value = e;
					if (logger.isDebugEnabled()) logger.debug("Expected exception occurred: " + e.getMessage() + ", time to countdown the latch");
					latch.countDown();
				}
			};

			// Now register an exception-listener on the connector that expects to fail
			currentExceptionListener = muleContext.getRegistry().lookupConnector(expectedFailingConnector).getExceptionListener();
			muleContext.getRegistry().lookupConnector(expectedFailingConnector).setExceptionListener(listener);

			// Initiate the test by sending a file to the SFTP server, which the inbound-endpoint then can pick up

			// Prepare message headers, set filename-header and if supplied any headers supplied in the call.
			Map<String, String> headers = new HashMap<String, String>();
			headers.put("filename", p.getFilename());
			if (p.getHeaders() != null) {
				headers.putAll(p.getHeaders());
			}

			// Setup connect string and perform the actual dispatch
			String connectString = (p.getSftpConnector() == null) ? "" : "?connector=" + p.getSftpConnector();
			muleClient.dispatch(getAddressByEndpoint(muleClient, p.getInboundEndpoint()) + connectString, TEST_MESSAGE, headers);
			
			// Wait for the exception to occur...
			if (logger.isDebugEnabled()) logger.debug("Waiting for an exception to occur...");
			boolean workDone = latch.await(p.getTimeout(), TimeUnit.MILLISECONDS);
			if (logger.isDebugEnabled()) logger.debug((workDone) ? "Exception occurred, continue..." : "No exception, instead a timeout occurred!");
			
			// Raise a fault if the test timed out
			assertTrue("Test timed out. It took more than " + p.getTimeout() + " milliseconds. If this error occurs the test probably needs a longer time out (on your computer/network)", workDone);

		} catch (Exception e) {
			e.printStackTrace();
			fail("An unexpected error occurred: " + e.getMessage());

		} finally {
			// Dispose muleClient if created locally
			if (localMuleClient) muleClient.dispose();
			
			// Always reset the current listener
			muleContext.getRegistry().lookupConnector(expectedFailingConnector).setExceptionListener(currentExceptionListener);
		}
		
		return (Exception)exceptionHolder.value;
    }

	/**
	 * Helper class for dynamic assignment of parameters to the method dispatchAndWaitForDelivery()
	 * Only inboundEndpoint and outboundEndpoint are mandatory, the rest of the parameters are optional.
	 * 
	 * @author Magnus Larsson
	 */
	public class DispatchParameters {
		
		/**
		 * Optional MuleClient, the method will create an own if not supplied.
		 */
		private MuleClient muleClient = null;

		/**
		 * Optional name of sftp-connector, if not supplied it is assumed that only one sftp-conector is speficied in the mule configuration.
		 * If more than one sftp-connector is specified this paramter has to be specified to point out what connector to use for the dispatch.
		 */
		private String sftpConnector = null;
		
		/** 
		 * Mandatory name of the inbound endpoint, i.e. where to dispatch the file
		 */
		private String inboundEndpoint = null;

		/**
		 * Optional message headers
		 */
		private Map<String, String> headers = null;
				
		/**
		 * Optional content of the file, if not specified then it defaults to AbstractMuleTestCase.TEST_MESSAGE.
		 */
		private String message = TEST_MESSAGE;

		/**
		 * Optional name of the file, defaults to FILE_NAME
		 */
		private String filename = FILE_NAME;
		
		/**
		 * Mandatory name of the outbound endpoint, i.e. where we will wait for a message to be delivered to in the end
		 */
		private String outboundEndpoint = null;
		
		/** 
		 * Optional timeout for how long we will wait for a message to be delivered to the outbound endpoint
		 */
		private long timeout = 10000;
		
		public DispatchParameters(String inboundEndpoint, String outboundEndpoint) {
			this.inboundEndpoint = inboundEndpoint;
			this.outboundEndpoint = outboundEndpoint;
		}

		public MuleClient getMuleClient() {
			return muleClient;
		}

		public void setMuleClient(MuleClient muleClient) {
			this.muleClient = muleClient;
		}

		public String getSftpConnector() {
			return sftpConnector;
		}

		public void setSftpConnector(String sftpConnector) {
			this.sftpConnector = sftpConnector;
		}

		public String getInboundEndpoint() {
			return inboundEndpoint;
		}

		public void setInboundEndpoint(String inboundEndpoint) {
			this.inboundEndpoint = inboundEndpoint;
		}

		public Map<String, String> getHeaders() {
			return headers;
		}

		public void setHeaders(Map<String, String> headers) {
			this.headers = headers;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getFilename() {
			return filename;
		}

		public void setFilename(String filename) {
			this.filename = filename;
		}

		public String getOutboundEndpoint() {
			return outboundEndpoint;
		}

		public void setOutboundEndpoint(String outboundEndpoint) {
			this.outboundEndpoint = outboundEndpoint;
		}

		public long getTimeout() {
			return timeout;
		}

		public void setTimeout(long timeout) {
			this.timeout = timeout;
		}

				
	}

}