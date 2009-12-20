/*
 * $Id: SftpLargeReceiveFunctionalTestCase.java 60 2008-04-24 22:42:00Z quoc $
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the MPL style
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */


package org.mule.transport.sftp;

import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.module.client.MuleClient;
 
/**
 * @author Magnus Larsson
 * <code>SftpExpressionFilenameParserTestCase</code> tests usage of the Expression Filename Parser instead of the default Legacy Parser.
 */

public class SftpExpressionFilenameParserTestCase extends AbstractSftpTestCase
{
	protected static final long TIMEOUT = 10000;
	private static final String OUTBOUND_ENDPOINT_NAME = "outboundEndpoint";
	private static final String INBOUND_ENDPOINT_NAME = "inboundEndpoint";

	protected String getConfigResources()
	{
		return "mule-sftp-expressionFilenaemParser-config.xml";
	}

    @Override
    protected void doSetUp() throws Exception {
        super.doSetUp();

        initEndpointDirectory(INBOUND_ENDPOINT_NAME);
        initEndpointDirectory(OUTBOUND_ENDPOINT_NAME);
    }

	public void testExpressionFilenameParser() throws Exception
	{
		MuleClient muleClient = new MuleClient();
		dispatchAndWaitForDelivery(new DispatchParameters(INBOUND_ENDPOINT_NAME, OUTBOUND_ENDPOINT_NAME));

		SftpClient sftpClient = null;
        try {
    		// Make sure a new file with name according to the notation has been created
    		sftpClient = getSftpClient(muleClient, OUTBOUND_ENDPOINT_NAME);
    		ImmutableEndpoint endpoint = (ImmutableEndpoint) muleClient.getProperty(OUTBOUND_ENDPOINT_NAME);
    		assertTrue("A new file in the outbound endpoint should exist", super.verifyFileExists(sftpClient, endpoint.getEndpointURI().getPath(), FILE_NAME));
        } finally {
            sftpClient.disconnect();
        }
    }
}
