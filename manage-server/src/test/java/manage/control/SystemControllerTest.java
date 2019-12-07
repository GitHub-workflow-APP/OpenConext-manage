package manage.control;

import manage.AbstractIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("unchecked")
public class SystemControllerTest extends AbstractIntegrationTest {

    @Value("${push.eb.user}")
    private String pushUser;

    @Value("${push.eb.password}")
    private String pushPassword;

    @Test
    public void pushPreview() throws Exception {
        Map connections = given()
                .when()
                .get("manage/api/client/playground/pushPreview")
                .then()
                .statusCode(SC_OK)
                .extract().as(Map.class);
        Map expected = objectMapper.readValue(readFile("push/push.expected_connections.json"), Map.class);

        assertEquals(expected, connections);
    }

    @Test
    public void validate() throws Exception {
        String body = given()
                .when()
                .get("manage/api/client/playground/validate")
                .then()
                .statusCode(SC_OK)
                .extract().asString();
        assertEquals("{}", body);
    }

    @Test
    public void orphans() throws Exception {
        List orphans = given()
                .when()
                .get("manage/api/client/playground/orphans")
                .then()
                .statusCode(SC_OK)
                .extract().as(List.class);
        List expected = objectMapper.readValue(readFile("json/expected_orphans.json"), List.class);

        assertEquals(expected, orphans);

        given()
                .when()
                .delete("manage/api/client/playground/deleteOrphans")
                .then()
                .statusCode(SC_OK);

        given()
                .when()
                .get("manage/api/client/playground/orphans")
                .then()
                .statusCode(SC_OK)
                .body("size()", is(0));
    }
}