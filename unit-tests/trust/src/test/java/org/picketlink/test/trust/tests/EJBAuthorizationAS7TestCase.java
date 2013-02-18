/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors. 
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.picketlink.test.trust.tests;

import java.io.File;
import java.security.Provider;
import java.security.Security;
import java.util.Hashtable;

import javax.ejb.EJBAccessException;
import javax.naming.Context;
import javax.naming.InitialContext;

import junit.framework.Assert;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.picketlink.identity.federation.bindings.jboss.auth.SAML2STSLoginModule;
import org.picketlink.identity.federation.core.saml.v2.util.DocumentUtil;
import org.picketlink.test.integration.util.PicketLinkIntegrationTests;
import org.picketlink.test.integration.util.TargetContainers;
import org.picketlink.test.trust.ejb.EchoService;
import org.picketlink.test.trust.ejb.EchoServiceImpl;
import org.w3c.dom.Element;

/**
 * <p>
 * Tests the invocation of EJBs protected by the {@link SAML2STSLoginModule}.
 * </p>
 * 
 * TODO: Currently disabled because the SASL PLAIN mechanism is only available for JBoss AS 7.1.3+ and 7.2.0+.
 * 
 * @author <a href="mailto:psilva@redhat.com">Pedro Silva</a>
 */
@RunWith(PicketLinkIntegrationTests.class)
@TargetContainers ({"jbas7"})
public class EJBAuthorizationAS7TestCase extends TrustTestsBase {

    @Deployment(name = "ejb-test", testable = false)
    @TargetsContainer("jboss")
    public static JavaArchive createEJBTestDeployment() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "ejb-test.jar");

        archive.addClass(EchoService.class);
        archive.addClass(EchoServiceImpl.class);
        archive.addAsManifestResource(new File(EJBAuthorizationAS7TestCase.class.getClassLoader()
                .getResource("jboss-deployment-structure.xml").getPath()));
        archive.addAsResource(
                new File(EJBAuthorizationAS7TestCase.class.getClassLoader().getResource("props/sts-users.properties").getPath()),
                ArchivePaths.create("users.properties"));
        archive.addAsResource(
                new File(EJBAuthorizationAS7TestCase.class.getClassLoader().getResource("props/sts-roles.properties").getPath()),
                ArchivePaths.create("roles.properties"));

        return archive;
    }

    @Test
    public void testSuccessfulEJBInvocation() throws Exception {
        // add the JDK SASL Provider that allows to use the PLAIN SASL Client
        //Security.addProvider(new Provider());
    	addProvider();

        Element assertion = getAssertionFromSTS("UserA", "PassA");
        
        // JNDI environment configuration properties
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        
        env.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        env.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        env.put("java.naming.provider.url", "remote://localhost:4447");
        env.put("jboss.naming.client.ejb.context", "true");
        env.put("jboss.naming.client.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT", "false");
        env.put("javax.security.sasl.policy.noplaintext", "false");
        
        // provide the user principal and credential. The credential is the previously issued SAML assertion
        env.put(Context.SECURITY_PRINCIPAL, "admin");
        env.put(Context.SECURITY_CREDENTIALS, DocumentUtil.getNodeAsString(assertion));
        
        // create the JNDI Context and perform the authentication using the SAML2STSLoginModule
        Context context = new InitialContext(env);
        
        // lookup the EJB
        EchoService object = (EchoService) context.lookup("ejb-test/EchoServiceImpl!org.picketlink.test.trust.ejb.EchoService");
        
        // If everything is ok the Principal name will be added to the message
        Assert.assertEquals("Hi UserA", object.echo("Hi "));
    }

    @Test(expected = EJBAccessException.class)
    public void testNotAuthorizedEJBInvocation() throws Exception {
        // add the JDK SASL Provider that allows to use the PLAIN SASL Client
        //Security.addProvider(new Provider());
    	addProvider();
    	
        // issue a new SAML Assertion using the PicketLink STS
        Element assertion = getAssertionFromSTS("UserA", "PassA");

        // JNDI environment configuration properties
        Hashtable<String, Object> env = new Hashtable<String, Object>();

        env.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        env.put("java.naming.factory.initial", "org.jboss.naming.remote.client.InitialContextFactory");
        env.put("java.naming.provider.url", "remote://localhost:4447");
        env.put("jboss.naming.client.ejb.context", "true");
        env.put("jboss.naming.client.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT", "false");
        env.put("javax.security.sasl.policy.noplaintext", "false");

        // provide the user principal and credential. The credential is the previously issued SAML assertion
        env.put(Context.SECURITY_PRINCIPAL, "UserA");
        env.put(Context.SECURITY_CREDENTIALS, DocumentUtil.getNodeAsString(assertion));

        // create the JNDI Context and perform the authentication using the SAML2STSLoginModule
        Context context = new InitialContext(env);

        // lookup the EJB
        EchoService object = (EchoService) context.lookup("ejb-test/EchoServiceImpl!org.picketlink.test.trust.ejb.EchoService");

        // If everything is ok the Principal name will be added to the message
        Assert.assertEquals("Hi UserA", object.echoUnchecked("Hi "));
    }
    
    /*
     * add the JDK SASL Provider or add 
     */
    private void addProvider() {
    	try {
            Provider provider = (Provider) Class.forName("com.sun.security.sasl.Provider")
            		.getConstructor().newInstance();
            Security.addProvider(provider);
	    } catch (Exception e) {
	    	try {
	    		Provider provider = (Provider) Class.forName("com.ibm.security.sasl.IBMSASL")
	            		.getConstructor().newInstance();
	            Security.addProvider(provider);
	    	} catch (Exception ex) { 
	            System.err.println("Unable to register com.sun.security.sasl.Provider or com.ibm.security.sasl.IBMSASL security provider.");
	            e.printStackTrace();
	    	}
	    }
    }

}