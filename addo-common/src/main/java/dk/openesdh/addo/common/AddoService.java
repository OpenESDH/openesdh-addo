package dk.openesdh.addo.common;

import dk.openesdh.addo.exception.AddoException;

public interface AddoService {

    public String login(String username, String password) throws AddoException;

    public String getSigningTemplates(String username, String password) throws AddoException;
}
