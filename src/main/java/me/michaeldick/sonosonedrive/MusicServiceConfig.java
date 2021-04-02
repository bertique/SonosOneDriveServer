/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Sonos, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE
 */

package me.michaeldick.sonosonedrive;

import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMResult;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.SoapFaultDetailElement;
import org.springframework.ws.soap.server.endpoint.SoapFaultAnnotationExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.SimpleWsdl11Definition;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.sonos.services.DeviceLinkTokenFailureException;
import com.sonos.services.DeviceLinkTokenRetryException;
import com.sonos.services.SonosCustomFault;
import com.sonos.services.security.AuthTokenRefreshRequiredException;
import com.sonos.services.security.ContentApiCredentialsInterceptor;
import com.sonos.services.CustomFault;

@EnableWs
@Configuration
public class MusicServiceConfig extends WsConfigurerAdapter{
    public static final String SONOS_SOAP_NAMESPACE = "http://www.sonos.com/Services/1.1";

    @Bean
    public ServletRegistrationBean messageDispatcherServlet(ApplicationContext applicationContext) {
        return new ServletRegistrationBean( new MessageDispatcherServlet(){{
            setApplicationContext(applicationContext);
            setTransformWsdlLocations(true);
        }}, "/soap/*");
    }

    @Bean(name = "soapFaultAnnotationExceptionResolver")
    public SoapFaultAnnotationExceptionResolver exceptionResolver(ApplicationContext applicationContext) {
        SoapFaultAnnotationExceptionResolver exceptionResolver = new SonosCustomFaultExceptionResolver();
        SoapFaultDefinition soapFaultDefinition = new SoapFaultDefinition();
        soapFaultDefinition.setFaultCode(new QName(SONOS_SOAP_NAMESPACE, "Server"));
        soapFaultDefinition.setFaultStringOrReason("Unknown");

        exceptionResolver.setDefaultFault(soapFaultDefinition);

        exceptionResolver.setOrder(1);
        return exceptionResolver;
    }

    @Bean
    public MusicServiceAnnotationMethodEndpointMapping payloadRootAnnotationMethodEndpointMapping() {
        logger.info("Adding MusicServiceAnnotationMethodEndpointMapping");

        return new MusicServiceAnnotationMethodEndpointMapping() {{
            setOrder(0);
            // Here we inject the ContentApiCredentialsInterceptor to check access tokens in SOAP headers, for requests
            // to the music service endpoint.
            // Note that this interceptor only applies to the music service API endpoint, and not to, e.g., static URLs
            // for mp3 files. In a production system, actual media might be served from a completely different endpoint.
            setInterceptors(new EndpointInterceptor[] { new ContentApiCredentialsInterceptor() });
        }};
    }

    @Bean(name = "contentapi")
    public SimpleWsdl11Definition simpleWsdl11Definition() {
        return new SimpleWsdl11Definition(new ClassPathResource("Sonos.wsdl"));
    }

    private class SonosCustomFaultExceptionResolver extends SoapFaultAnnotationExceptionResolver {
        @Override
        protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
            SonosCustomFault msg;
            if (ex instanceof SonosCustomFault) {
                msg = (SonosCustomFault)ex;
            } else {
                msg = new SonosCustomFault(ex.getMessage(), -1, ex.getMessage());
            }

            if (ex instanceof DeviceLinkTokenRetryException ||
                    ex instanceof DeviceLinkTokenFailureException ||
                    ex instanceof AuthTokenRefreshRequiredException) {
                SoapFaultDetail detail = fault.addFaultDetail();

                if (ex instanceof AuthTokenRefreshRequiredException) {
                    AuthTokenRefreshRequiredException atrrex = (AuthTokenRefreshRequiredException)ex;
                    SoapFaultDetailElement resultElement = detail.addFaultDetailElement(new QName(SONOS_SOAP_NAMESPACE,
                            "refreshAuthTokenResult"));

                    // looks like we have to do all this just to add children to the fault detail element
                    DOMResult reResult = (DOMResult)resultElement.getResult();
                    Node reRootNode = reResult.getNode();
                    Document reDoc = reRootNode.getOwnerDocument();

                    Node tokenElement = reDoc.createElementNS(SONOS_SOAP_NAMESPACE, "authToken");
                    tokenElement.appendChild(reDoc.createTextNode(atrrex.token));
                    reRootNode.appendChild(tokenElement);

                    Node keyElement = reDoc.createElementNS(SONOS_SOAP_NAMESPACE, "privateKey");
                    keyElement.appendChild(reDoc.createTextNode(atrrex.key));
                    reRootNode.appendChild(keyElement);
                } else {
                    CustomFault sonosDetail = msg.getCustomFaultDetail();

                    if (sonosDetail.getExceptionInfo() == null) {
                        logger.warn("exception info is null");
                    }

                    if (sonosDetail.getSonosError() == null) {
                        logger.warn("sonos error is null");
                    }
                    detail.addFaultDetailElement(new QName(SONOS_SOAP_NAMESPACE,
                            "ExceptionInfo")).addText(sonosDetail.getExceptionInfo());
                    detail.addFaultDetailElement(new QName(SONOS_SOAP_NAMESPACE,
                            "SonosError")).addText("" + sonosDetail.getSonosError());
                }
            }
        }
    }
}
