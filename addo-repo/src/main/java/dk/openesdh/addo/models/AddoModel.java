package dk.openesdh.addo.models;

import org.alfresco.service.namespace.QName;

public class AddoModel {

    public static final String ADDO_URI = "http://openesdh.dk/model/addo/1.0";
    public static final String ADDO_PREFIX = "addo";

    public static final QName ASPECT_ADDO_SIGNED = QName.createQName(ADDO_URI, "addoSigned");
    public static final QName ASPECT_ADDO_CREDENTIALS = QName.createQName(ADDO_URI, "addoSigned");

    public static final QName PROP_ADDO_TOKEN = QName.createQName(ADDO_URI, "addoToken");
    public static final QName PROP_ADDO_USERNAME = QName.createQName(ADDO_URI, "addoUsername");
    public static final QName PROP_ADDO_PASSWORD = QName.createQName(ADDO_URI, "addoPassword");
}
