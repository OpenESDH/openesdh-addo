package dk.openesdh.addo.services;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfSigningDocument;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfSigningRecipientData;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfSigningSigningSequenceItem;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ArrayOfValidationError;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.GetSigningStatus;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.GetSigningTemplatesResponse;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.Signing;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningDocument;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningInboundEnclosuresCollection;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningRecipientData;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningSigningSequenceItem;
import org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.SigningSigningSequenceOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dk.vismaaddo.schemas.services.signingservice._2014._09.SigningService;
import dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningrequest._2014._09.InitiateSigningRequest;
import dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningresponse._2014._09.InitiateSigningResponse;

import dk.openesdh.addo.exception.AddoException;
import dk.openesdh.addo.model.AddoDocument;
import dk.openesdh.addo.model.AddoRecipient;
import dk.openesdh.addo.webservices.AddoWebService;

@Component("addoService")
public class AddoServiceImpl implements AddoService {

    @Autowired
    @Qualifier("AddoWebService")
    private AddoWebService webService;

    @Override
    public Boolean tryLogin(String username, String password) {
        return webService.tryLogin(username, password);
    }

    @Override
    public String getSigningTemplates(String username, String password) {
        SigningService port = webService.getPort(username, password);
        String token = port.login(username, password);
        GetSigningTemplatesResponse signingTemplates = port.getSigningTemplates(token);
        return webService.jaxbToJsonString(signingTemplates.getSigningTemplateItems());
    }

    @Override
    public String initiateSigning(
            String username,
            String password,
            String signingTemplateId,
            String signingTemplateName,
            List<AddoDocument> documents,
            List<AddoDocument> enclosureDocuments,
            List<AddoRecipient> recipients,
            boolean useSequentialSigning) throws AddoException {
        SigningService port = webService.getPort(username, password);
        String token = port.login(username, password);

        dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningrequest._2014._09.ObjectFactory factory
                = new dk.vismaaddo.schemas.services.signingservice.messages.initiatesigningrequest._2014._09.ObjectFactory();

        org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ObjectFactory signingFactory
                = new org.datacontract.schemas._2004._07.visma_addo_webservice_contracts_v1_0.ObjectFactory();

        /**
         * Documents
         */
        ArrayOfSigningDocument addoDocuments = signingFactory.createArrayOfSigningDocument();
        documents.stream().map((doc) -> {
            /**
             * SigningDocument
             */
            SigningDocument signingDocument = signingFactory.createSigningDocument();
            signingDocument.setData(signingFactory.createSigningDocumentData(Base64.getEncoder().encodeToString(doc.getData())));
            //signingDocument.setId("");
            signingDocument.setIsShared(Boolean.FALSE);
            signingDocument.setMimeType(signingFactory.createSigningDocumentMimeType(doc.getMimeType()));
            signingDocument.setName(signingFactory.createSigningDocumentName(doc.getName()));
            return signingDocument;
        }).forEach((signingDocument) -> {
            //add
            addoDocuments.getSigningDocument().add(signingDocument);
        });

        /**
         * EnclosureDocuments
         */
        ArrayOfSigningDocument addoEnclosureDocuments = signingFactory.createArrayOfSigningDocument();
        enclosureDocuments.stream().map((enc) -> {
            /**
             * SigningDocument
             */
            SigningDocument signingDocument = signingFactory.createSigningDocument();
            signingDocument.setData(signingFactory.createSigningDocumentData(Base64.getEncoder().encodeToString(enc.getData())));
            //signingDocument.setId("");
            signingDocument.setIsShared(Boolean.FALSE);
            signingDocument.setMimeType(signingFactory.createSigningDocumentMimeType(enc.getMimeType()));
            signingDocument.setName(signingFactory.createSigningDocumentName(enc.getName()));
            return signingDocument;
        }).forEach((signingDocument) -> {
            //add
            addoDocuments.getSigningDocument().add(signingDocument);
        });

        /**
         * InboundEnclosures
         */
        SigningInboundEnclosuresCollection inboundEnclosures = signingFactory.createSigningInboundEnclosuresCollection();
        inboundEnclosures.setAllowAll(Boolean.FALSE);
        inboundEnclosures.setDocumentReferences(
                signingFactory.createSigningInboundEnclosuresCollectionDocumentReferences(
                        signingFactory.createArrayOfSigningDocumentInboundEnclosureReference()));

        /**
         * Recipients
         */
        ArrayOfSigningRecipientData addoRecipients = signingFactory.createArrayOfSigningRecipientData();
        /**
         * ArrayOfSigningSigningSequenceItem
         */
        ArrayOfSigningSigningSequenceItem signingSigningSequenceItems = signingFactory.createArrayOfSigningSigningSequenceItem();

        recipients.stream().forEach((rec) -> {
            String recipientId = UUID.randomUUID().toString();
            /**
             * RecipientData
             */
            SigningRecipientData recipientData = signingFactory.createSigningRecipientData();
            recipientData.setId(recipientId);
            recipientData.setAddress(signingFactory.createSigningRecipientDataAddress(rec.getAddress()));
            recipientData.setCpr(signingFactory.createSigningRecipientDataCpr(rec.getCpr()));
            recipientData.setEmail(signingFactory.createSigningRecipientDataEmail(rec.getEmail()));
            recipientData.setName(signingFactory.createSigningRecipientDataName(rec.getName()));
            recipientData.setPhone(signingFactory.createSigningRecipientDataPhone(rec.getPhone()));
            recipientData.setSignedDate(signingFactory.createSigningRecipientDataSignedDate(null));
            recipientData.setTemplateData(signingFactory.createSigningRecipientDataTemplateData(null));
            addoRecipients.getSigningRecipientData().add(recipientData);
            if (useSequentialSigning) {
                /**
                 * SigningSigningSequenceItem
                 */
                SigningSigningSequenceItem signingSigningSequenceItem = signingFactory.createSigningSigningSequenceItem();
                signingSigningSequenceItem.setRecipientId(recipientId);

                signingSigningSequenceItems.getSigningSigningSequenceItem().add(signingSigningSequenceItem);
            }
        });

        /**
         * Signing
         */
        Signing signingObject = signingFactory.createSigning();
        signingObject.setDocuments(signingFactory.createSigningDocuments(addoDocuments));
        signingObject.setEnclosureDocuments(signingFactory.createSigningEnclosureDocuments(addoEnclosureDocuments));
        signingObject.setInboundEnclosures(signingFactory.createSigningInboundEnclosures(inboundEnclosures));
        signingObject.setRecipients(signingFactory.createSigningRecipients(addoRecipients));

        if (useSequentialSigning) {
            /**
             * SigningSigningSequenceOrder
             */
            SigningSigningSequenceOrder signingSigningSequenceOrder = signingFactory.createSigningSigningSequenceOrder();
            signingSigningSequenceOrder.setSigningSequenceItems(signingFactory.createSigningSigningSequenceOrderSigningSequenceItems(signingSigningSequenceItems));
            signingObject.setSigningSequence(signingFactory.createSigningSigningSequenceOrder(signingSigningSequenceOrder));
        }

        InitiateSigningRequest requestObject = factory.createInitiateSigningRequest();
        requestObject.setName(factory.createInitiateSigningRequestName(signingTemplateName));
        requestObject.setSigningData(factory.createInitiateSigningRequestSigningData(signingObject));
        requestObject.setSigningTemplateId(signingTemplateId);
        requestObject.setStartDate(webService.gregorianCalendarOf(new Date()));

        InitiateSigningResponse initiateSigning = port.initiateSigning(token, requestObject, null);
        if (!initiateSigning.getSigningToken().isNil()) {
            return initiateSigning.getSigningToken().getValue();
        }
        ArrayOfValidationError errors = initiateSigning.getValidationData().getValue().getValidationErrors().getValue();
        errors.getValidationError()
                .stream()
                .findFirst()
                .ifPresent(webService::throwError);
        throw new RuntimeException("Signing token is missing");
    }

    @Override
    public String getSigningStatus(String username, String password, String signingToken) {
        SigningService port = webService.getPort(username, password);
        String guid = port.login(username, password);
        GetSigningStatus signingStatus = port.getSigningStatus(guid, signingToken);
        dk.vismaaddo.schemas.services.signingservice._2014._09.ObjectFactory f
                = new dk.vismaaddo.schemas.services.signingservice._2014._09.ObjectFactory();
        return webService.jaxbToJsonString(f.createGetSigningStatusResponseGetSigningStatusResult(signingStatus));
    }

    void setWebService4testing(AddoWebService webService) {
        this.webService = webService;
    }
}
