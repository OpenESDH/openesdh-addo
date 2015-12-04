package dk.openesdh.addo.services;

import dk.openesdh.addo.model.AddoPasswordEncoder;
import dk.openesdh.addo.webservices.AddoWebService;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class AddoServiceImplIT {

    //addo credentials can be added here, or as system property -DaddoUser=username -DaddoPw=password
    private static final String USERNAME = System.getProperty("addoUser", "add_user_here");
    private static final String PASSWORD = AddoPasswordEncoder.encode(System.getProperty("addoPw", "add_password_here"));
    private AddoServiceImpl service;

    @Before
    public void setUp() {
        System.setProperty("https.protocols", "TLSv1,SSLv3,SSLv2Hello");
        service = new AddoServiceImpl();
        service.setWebService4testing(new AddoWebService());
    }

    @Test
    public void passwordsAreEncodedCorrectlly() {
        assertEquals(
                "64fGMWX6f6W+BR7VDTBfz/LS4jVH7XPUSmg7exb5zA2H3bkGcqIslDv9wwSArK20KJVu3/+JsHBPkVgV3FM0Cg==",
                AddoPasswordEncoder.encode("ThisIsEncodedPassword123"));
    }

    @Test
    public void testTryLogin() {
        assertFalse(service.tryLogin(USERNAME, "wrong_password"));
        assertTrue(service.tryLogin(USERNAME, PASSWORD));
    }

    @Test
    public void testGetSigningTemplates() {
        System.out.println(service.getSigningTemplates(USERNAME, PASSWORD));
    }

    @Ignore
    @Test
    public void testInitiateSigning() {
    }

    @Test
    public void testGetSigningStatus() {
        System.out.println(service.getSigningStatus(USERNAME, PASSWORD, "3B13-55C4-B06D"));
    }

}
