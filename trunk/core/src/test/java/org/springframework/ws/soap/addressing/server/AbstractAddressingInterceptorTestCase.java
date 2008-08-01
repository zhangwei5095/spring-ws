/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ws.soap.addressing.server;

import java.net.URI;
import java.util.Iterator;
import java.util.Locale;

import org.easymock.MockControl;

import org.springframework.ws.context.DefaultMessageContext;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.addressing.AbstractWsAddressingTestCase;
import org.springframework.ws.soap.addressing.messageid.MessageIdStrategy;
import org.springframework.ws.soap.addressing.version.AddressingVersion;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.WebServiceMessageSender;

public abstract class AbstractAddressingInterceptorTestCase extends AbstractWsAddressingTestCase {

    private AddressingEndpointInterceptor interceptor;

    private MockControl strategyControl;

    private MessageIdStrategy strategyMock;

    protected final void onSetUp() throws Exception {
        strategyControl = MockControl.createControl(MessageIdStrategy.class);
        strategyMock = (MessageIdStrategy) strategyControl.getMock();
        strategyControl.expectAndDefaultReturn(strategyMock.isDuplicate(null), false);
        URI replyAction = new URI("urn:replyAction");
        URI faultAction = new URI("urn:faultAction");
        interceptor = new AddressingEndpointInterceptor(getVersion(), strategyMock, new WebServiceMessageSender[0],
                replyAction, faultAction);
    }

    public void testUnderstands() throws Exception {
        SaajSoapMessage validRequest = loadSaajMessage(getTestPath() + "/valid.xml");
        Iterator iterator = validRequest.getSoapHeader().examineAllHeaderElements();
        strategyControl.replay();
        while (iterator.hasNext()) {
            SoapHeaderElement headerElement = (SoapHeaderElement) iterator.next();
            assertTrue("Header [" + headerElement.getName() + " not understood",
                    interceptor.understands(headerElement));
        }
        strategyControl.verify();
    }

    public void testValidRequest() throws Exception {
        SaajSoapMessage valid = loadSaajMessage(getTestPath() + "/valid.xml");
        MessageContext context = new DefaultMessageContext(valid, new SaajSoapMessageFactory(messageFactory));
        strategyControl.replay();
        boolean result = interceptor.handleRequest(context, null);
        assertTrue("Valid request not handled", result);
        assertFalse("Message Context has response", context.hasResponse());
        strategyControl.verify();
    }

    public void testNoMessageId() throws Exception {
        SaajSoapMessage valid = loadSaajMessage(getTestPath() + "/request-no-message-id.xml");
        MessageContext context = new DefaultMessageContext(valid, new SaajSoapMessageFactory(messageFactory));
        strategyControl.replay();
        boolean result = interceptor.handleRequest(context, null);
        assertFalse("Request with no MessageID handled", result);
        assertTrue("Message Context has no response", context.hasResponse());
        SaajSoapMessage expectedResponse = loadSaajMessage(getTestPath() + "/response-no-message-id.xml");
        assertXMLEqual("Invalid response for message with no MessageID", expectedResponse,
                (SaajSoapMessage) context.getResponse());
        strategyControl.verify();
    }

    public void testNoReplyTo() throws Exception {
        SaajSoapMessage valid = loadSaajMessage(getTestPath() + "/request-no-reply-to.xml");
        MessageContext context = new DefaultMessageContext(valid, new SaajSoapMessageFactory(messageFactory));
        URI messageId = new URI("uid:1234");
        strategyControl.expectAndReturn(strategyMock.newMessageId((SoapMessage) context.getResponse()), messageId);
        strategyControl.replay();
        boolean result = interceptor.handleResponse(context, null);
        assertTrue("Request with no ReplyTo not handled", result);
        assertTrue("Message Context has no response", context.hasResponse());
        SaajSoapMessage expectedResponse = loadSaajMessage(getTestPath() + "/response-anonymous.xml");
        assertXMLEqual("Invalid response for message with invalid MAP", expectedResponse,
                (SaajSoapMessage) context.getResponse());
        strategyControl.verify();
    }

    public void testAnonymousReplyTo() throws Exception {
        SaajSoapMessage valid = loadSaajMessage(getTestPath() + "/request-anonymous.xml");
        MessageContext context = new DefaultMessageContext(valid, new SaajSoapMessageFactory(messageFactory));
        URI messageId = new URI("uid:1234");
        strategyControl.expectAndReturn(strategyMock.newMessageId((SoapMessage) context.getResponse()), messageId);
        strategyControl.replay();
        boolean result = interceptor.handleResponse(context, null);
        assertTrue("Request with anonymous ReplyTo not handled", result);
        SaajSoapMessage expectedResponse = loadSaajMessage(getTestPath() + "/response-anonymous.xml");
        assertXMLEqual("Invalid response for message with invalid MAP", expectedResponse,
                (SaajSoapMessage) context.getResponse());
        strategyControl.verify();
    }

    public void testNoneReplyTo() throws Exception {
        SaajSoapMessage valid = loadSaajMessage(getTestPath() + "/request-none.xml");
        MessageContext context = new DefaultMessageContext(valid, new SaajSoapMessageFactory(messageFactory));
        strategyControl.replay();
        boolean result = interceptor.handleResponse(context, null);
        assertFalse("None request handled", result);
        assertFalse("Message context has response", context.hasResponse());
        strategyControl.verify();
    }

    public void testFaultTo() throws Exception {
        SaajSoapMessage valid = loadSaajMessage(getTestPath() + "/request-fault-to.xml");
        MessageContext context = new DefaultMessageContext(valid, new SaajSoapMessageFactory(messageFactory));
        SaajSoapMessage response = (SaajSoapMessage) context.getResponse();
        response.getSoapBody().addServerOrReceiverFault("Error", Locale.ENGLISH);
        URI messageId = new URI("uid:1234");
        strategyControl.expectAndReturn(strategyMock.newMessageId((SoapMessage) context.getResponse()), messageId);
        strategyControl.replay();
        boolean result = interceptor.handleFault(context, null);
        assertTrue("Request with anonymous FaultTo not handled", result);
        SaajSoapMessage expectedResponse = loadSaajMessage(getTestPath() + "/response-fault-to.xml");
        assertXMLEqual("Invalid response for message with invalid MAP", expectedResponse,
                (SaajSoapMessage) context.getResponse());
        strategyControl.verify();
    }

    public void testOutOfBandReplyTo() throws Exception {
        MockControl senderControl = MockControl.createControl(WebServiceMessageSender.class);
        WebServiceMessageSender senderMock = (WebServiceMessageSender) senderControl.getMock();

        URI replyAction = new URI("urn:replyAction");
        URI faultAction = new URI("urn:replyAction");
        interceptor = new AddressingEndpointInterceptor(getVersion(), strategyMock,
                new WebServiceMessageSender[]{senderMock}, replyAction, faultAction);

        MockControl connectionControl = MockControl.createControl(WebServiceConnection.class);
        WebServiceConnection connectionMock = (WebServiceConnection) connectionControl.getMock();

        SaajSoapMessage valid = loadSaajMessage(getTestPath() + "/valid.xml");
        MessageContext context = new DefaultMessageContext(valid, new SaajSoapMessageFactory(messageFactory));
        SaajSoapMessage response = (SaajSoapMessage) context.getResponse();

        URI messageId = new URI("uid:1234");
        strategyControl.expectAndReturn(strategyMock.newMessageId((SoapMessage) context.getResponse()), messageId);

        URI uri = new URI("http://example.com/business/client1");
        senderControl.expectAndReturn(senderMock.supports(uri), true);
        senderControl.expectAndReturn(senderMock.createConnection(uri), connectionMock);
        connectionMock.send(response);
        connectionMock.close();

        strategyControl.replay();
        senderControl.replay();
        connectionControl.replay();

        boolean result = interceptor.handleResponse(context, null);
        assertFalse("Out of Band request handled", result);
        assertFalse("Message context has response", context.hasResponse());

        strategyControl.verify();
        senderControl.verify();
        connectionControl.verify();
    }

    protected abstract AddressingVersion getVersion();

    protected abstract String getTestPath();

}
