/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the BSD style
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.samples.sftp;

import org.mule.umo.UMOEventContext;
import org.mule.umo.lifecycle.Callable;

/**
 * <code>ExampleComponent</code> 
 * 
 * An example service component, if one were needed for this example
 * 
 */

public class ExampleComponent implements Callable
{
    
    public Object onCall(UMOEventContext context) throws Exception
    {        //Do nothing

        return null;
        
    }
}
