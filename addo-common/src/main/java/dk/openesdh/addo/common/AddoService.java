package dk.openesdh.addo.common;

import java.util.List;

import dk.openesdh.addo.exception.AddoException;

public interface AddoService {

    public Boolean tryLogin(String username, String password) throws AddoException;

    public AddoGUID login(String username, String password) throws AddoException;

    public String getSigningTemplates(AddoGUID guid) throws AddoException;

    public String initiateSigning(AddoGUID guid, String signingTemplateId, String signingTemplateName,
            AddoDocument document, List<AddoRecipient> recipients) throws AddoException;
}
