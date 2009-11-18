/*
 * $Id: SftpSendReceiveFunctionalTestCase.java 77 2009-07-17 08:13:22Z elhoo $
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the MPL style
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */


package org.mule.transport.sftp;


/**
 * Test sending and receiving a very large message.
 * <p/>
 * This test will probably fail due to the standard timeout. According to http://www.mulesource.org/display/MULE2USER/Functional+Testing
 * the only way to change the timeout is "add -Dmule.test.timeoutSecs=XX either to the mvn command you use to run Mule or to the JUnit
 * test runner in your IDE."
 *
 * Tested with '-Dmule.test.timeoutSecs=300'
 */
public class SftpSendReceiveLargeFileFunctionalTestCase extends AbstractSftpTestCase
{
	private static final long TIMEOUT = 300000;

	// Size of the generated stream - 200 Mb
	final static int SEND_SIZE = 1024 * 1024 * 200;

	public SftpSendReceiveLargeFileFunctionalTestCase() {
		// Increase the timeout of the test to 300 s
		logger.info("Timeout was set to: " + System.getProperty(PROPERTY_MULE_TEST_TIMEOUT, "-1"));
		System.setProperty(PROPERTY_MULE_TEST_TIMEOUT, "300000");
		logger.info("Timeout is now set to: " + System.getProperty(PROPERTY_MULE_TEST_TIMEOUT, "-1"));
	}

	// Uses the same config as SftpSendReceiveFunctionalTestCase
	protected String getConfigResources()
	{
		return "mule-send-receive-large-file-test-config.xml";
	}

    @Override
    protected void doSetUp() throws Exception {
        super.doSetUp();

        initEndpointDirectory("inboundEndpoint");
    }

	/**
	 * Test sending and receiving a large file.
	 *
	 */
	public void testSendAndReceiveLargeFile() throws Exception
	{
		executeBaseTest("inboundEndpoint", "vm://test.upload", "bigfile.txt", SEND_SIZE, "receiving", TIMEOUT);
	}
}
