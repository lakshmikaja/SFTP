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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.apache.log4j.Logger;
import org.mule.api.MuleEventContext;
import org.mule.api.MuleException;
import org.mule.api.endpoint.EndpointBuilder;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.module.client.MuleClient;
import org.mule.tck.FunctionalTestCase;
import org.mule.tck.functional.EventCallback;
import org.mule.tck.functional.FunctionalTestComponent;
import org.mule.util.StringMessageUtils;

import edu.emory.mathcs.backport.java.util.concurrent.CountDownLatch;
import edu.emory.mathcs.backport.java.util.concurrent.TimeUnit;
import edu.emory.mathcs.backport.java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Lennart Häggkvist
 *         Date: Jun 8, 2009
 */
public abstract class AbstractSftpTestCase extends FunctionalTestCase
{
    /**
     * Logger
     */
    private static final Logger log = Logger.getLogger(AbstractSftpTestCase.class);

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
				logger.debug("", e);
			}

		} catch (Exception e)
		{
			logger.debug("", e);
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

	protected String getPathByEndpoint(MuleClient muleClient, SftpClient sftpClient, String endpointName) throws IOException
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
		SftpClient sftpClient = new SftpClient();
		ImmutableEndpoint endpoint = getImmutableEndpoint(muleClient, endpointName);

		EndpointURI endpointURI = endpoint.getEndpointURI();
		SftpConnector sftpConnector = (SftpConnector) endpoint.getConnector();

//        if(!sftpClient.isConnected()) {
            sftpClient.connect(endpointURI.getHost());

            if (sftpConnector.getIdentityFile() != null)
            {
                assertTrue("Login failed", sftpClient.login(endpointURI.getUser(), sftpConnector.getIdentityFile(), sftpConnector.getPassphrase()));
            } else
            {
                assertTrue("Login failed", sftpClient.login(endpointURI.getUser(), endpointURI.getPassword()));
            }
//        }
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
				logger.info("called " + loopCount.incrementAndGet() + " times");
				FunctionalTestComponent ftc = (FunctionalTestComponent) component;

				InputStream sftpInputStream = (InputStream) ftc.getLastReceivedMessage();
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
								fail("Incorrect received byte (was '" + b + "', excepected '" + testByte + "'");
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

		logger.info(StringMessageUtils.getBoilerPlate("Note! If this test fails due to timeout please add '-Dmule.test.timeoutSecs=XX' to the mvn command!"));

		executeBaseAssertionsBeforeCall();

		// Send the content using stream
		client.send(sendUrl, os, props);

		latch.await(timeout, TimeUnit.MILLISECONDS);

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
		logger.info("Sent size: " + sendSize);
		logger.info("Received size: " + receivedSize);

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

	protected void remoteChmod(MuleClient muleClient, SftpClient sftpClient, String endpointName, int permissions) throws IOException, SftpException
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
                logger.error("", e);
            }

            String path = getPathByEndpoint(muleClient, sftpClient, endpointName);
            channelSftp.mkdir(path);
        } finally {
            sftpClient.disconnect();
        }
    }
}
