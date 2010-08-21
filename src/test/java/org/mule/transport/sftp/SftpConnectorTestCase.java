/*
 * $Id: ConnectorTestCase.vm 10787 2008-02-12 18:51:50Z dfeist $
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.sftp;

import org.mule.transport.AbstractConnectorTestCase;
import org.mule.api.service.Service;
import org.mule.api.transport.Connector;

public class SftpConnectorTestCase extends AbstractConnectorTestCase
{

    /* For general guidelines on writing transports see
       http://www.mulesource.org/display/MULE2USER/Creating+Transports
     */

    public Connector createConnector() throws Exception
    {
        /* IMPLEMENTATION NOTE: Create and initialise an instance of your
           connector here. Do not actually call the connect method. */

        SftpConnector c = new SftpConnector(muleContext);
        c.setName("Test");
        // TODO Set any additional properties on the connector here
        return c;
    }

    public String getTestEndpointURI()
    {
        return "sftp://ms/data";
    }

    public Object getValidMessage() throws Exception
    {
        return "payload";
    }


    public void testProperties() throws Exception
    {
        // TODO test setting and retrieving any custom properties on the
        // Connector as necessary
    }

    public void testConnectorMessageRequesterFactory()
    {
        //No MessageRequesterFactory
    }

}
