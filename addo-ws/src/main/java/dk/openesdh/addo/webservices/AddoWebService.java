package dk.openesdh.addo.webservices;

import java.io.*;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.xml.bind.*;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.apache.cxf.ws.security.trust.STSClient;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ValidationError;
import org.json.XML;
import org.tempuri.AddoSigningService;

import dk.vismaaddo.schemas.services.signingservice._2014._09.SigningService;

import dk.openesdh.addo.exception.AddoException;

public class AddoWebService {

    public static final String LOGIN_ERROR = "At least one security token in the message could not be validated.";

    public void init() throws MalformedURLException, IOException {
    }

    private AddoSigningService getService() {
        return new AddoSigningService(
                getClass().getResource("/original/SigningService.wsdl"),//TODO: change to configurable wsdlLocation;
                AddoSigningService.SigningService, new MTOMFeature(10240));
    }

    public SigningService getPort(String username, String password) {
        SigningService port = getService().getSigningService();

        ((BindingProvider) port).getRequestContext().put("ws-security.sts.client", new STSClient(CXFBusFactory.getDefaultBus()));
        ((BindingProvider) port).getRequestContext().put("ws-security.username", username);
        ((BindingProvider) port).getRequestContext().put("ws-security.password", password);

        Client client = ClientProxy.getClient(port);
        client.getInInterceptors().add(new GZIPInInterceptor());
        client.getOutInterceptors().add(new GZIPOutInterceptor());
        return port;
    }

    public Boolean tryLogin(String username, String password) {
        SigningService port = getPort(username, password);
        try {
            port.tryLogin();
            return true;
        } catch (SOAPFaultException e) {
            if (e.getMessage().equals(LOGIN_ERROR)) {
                return false;
            }
            throw e;
        }
    }

    public String jaxbToJsonString(JAXBElement jaxb) {
        try {
            JAXBContext jc = JAXBContext.newInstance(jaxb.getValue().getClass());
            Marshaller marshaller = jc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            marshaller.marshal(jaxb, out);
            return XML.toJSONObject(out.toString()).toString();
        } catch (JAXBException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void throwError(ValidationError err) throws AddoException {
        throw new AddoException(err.getMessage().getValue());
    }

    public XMLGregorianCalendar gregorianCalendarOf(Date date) {
        try {
            GregorianCalendar c = new GregorianCalendar();
            c.setTime(date);
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
        } catch (DatatypeConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
