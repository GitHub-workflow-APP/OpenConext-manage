package manage.control;

import manage.AbstractIntegrationTest;
import manage.model.Validation;
import org.junit.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

public class ValidationControllerTest extends AbstractIntegrationTest {

    @Test
    public void validationBoolean() throws Exception {
        doValidation("boolean", "1", true);
        doValidation("boolean", "0", true);
        doValidation("boolean", "nope", false);
    }

    @Test
    public void validationCertificate() throws Exception {
        doValidation("certificate",
                "MIIDEzCCAfugAwIBAgIJAKoK" +
                        "/heBjcOYMA0GCSqGSIb3DQEBBQUAMCAxHjAcBgNVBAoMFU9yZ2FuaXphdGlvbiwgQ049T0lEQzAeFw0xNTExMTExMDEyMTVaFw0yNTExMTAxMDEyMTVaMCAxHjAcBgNVBAoMFU9yZ2FuaXphdGlvbiwgQ049T0lEQzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANBGwJ/qpTQNiSgUglSE2UzEkUow+wS8r67etxoEhlzJZfgK/k5TfG1wICDqapHAxEVgUM10aBHRctNocA5wmlHtxdidhzRZroqHwpKy2BmsKX5Z2oK25RLpsyusB1KroemgA/CjUnI6rIL1xxFn3KyOFh1ZBLUQtKNQeMS7HFGgSDAp+sXuTFujz12LFDugX0T0KB5a1+0l8y0PEa0yGa1oi6seONx849ZHxM0PRvUunWkuTM+foZ0jZpFapXe02yWMqhc/2iYMieE/3GvOguJchJt6R+cut8VBb6ubKUIGK7pmoq/TB6DVXpvsHqsDJXechxcicu4pdKVDHSec850CAwEAAaNQME4wHQYDVR0OBBYEFK7RqjoodSYVXGTVEdLf3kJflP/sMB8GA1UdIwQYMBaAFK7RqjoodSYVXGTVEdLf3kJflP/sMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEFBQADggEBADNZkxlFXh4F45muCbnQd+WmaXlGvb9tkUyAIxVL8AIu8J18F420vpnGpoUAE+Hy3evBmp2nkrFAgmr055fAjpHeZFgDZBAPCwYd3TNMDeSyMta3Ka+oS7GRFDePkMEm+kH4/rITNKUF1sOvWBTSowk9TudEDyFqgGntcdu/l/zRxvx33y3LMG5USD0x4X4IKjRrRN1BbcKgi8dq10C3jdqNancTuPoqT3WWzRvVtB/q34B7F74/6JzgEoOCEHufBMp4ZFu54P0yEGtWfTwTzuoZobrChVVBt4w/XZagrRtUCDNwRpHNbpjxYudbqLqpi1MQpV9oht/BpTHVJG2i0ro=", true);
        doValidation("certificate", "nope", false);
    }

    @Test
    public void validationDateTime() throws Exception {
        doValidation("date-time", "2015-04-24T12:00:00Z", true);
        doValidation("date-time", "nope", false);
    }

    @Test
    public void validationEmail() throws Exception {
        doValidation("local-email", "conext-beheer@surfnet.nl", true);
        doValidation("local-email", "nope", false);
    }

    @Test
    public void validationNumber() throws Exception {
        doValidation("number", "1", true);
        doValidation("number", "nope", false);
    }

    @Test
    public void validationUrl() throws Exception {
        doValidation("url", "https://www.example.org", true);
        doValidation("url", "nope", false);
    }

    @Test
    public void validationUri() throws Exception {
        doValidation("uri", "nl.uva.myuva://redirect", true);
        doValidation("uri", "nope", false);
    }

    @Test
    public void validationXml() throws Exception {
        doValidation("xml", readFile("/xml/metadata_import_saml20_sp.xml"), true);
        doValidation("xml", "nope", false);
    }

    @Test
    public void validationJson() throws Exception {
        doValidation("json", readFile("/json/metadata_import_saml20_sp_nested.json"), true);
        doValidation("json", "nope:", false);
    }

    @Test
    public void validationUUID() throws Exception {
        doValidation("uuid", "ad93daef-0911-e511-80d0-005056956c1a", true);
        doValidation("uuid", "nope", false);
    }

    @Test
    public void secret() {
        Map<String, String> res = given()
                .when()
                .header("Content-type", "application/json")
                .get("manage/api/client/secret")
                .as(Map.class);
        assertEquals(36, res.get("secret").length());
    }

    private void doValidation(String type, String value, boolean valid) throws Exception {
        given()
                .when()
                .body(new Validation(type, value))
                .header("Content-type", "application/json")
                .post("manage/api/client/validation")
                .then()
                .statusCode(SC_OK)
                .body(equalTo(String.valueOf(valid)));
    }

}