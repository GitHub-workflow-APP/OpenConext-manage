package mr.control;

import mr.AbstractIntegrationTest;
import mr.migration.EntityType;
import mr.model.MetaData;
import mr.model.Revision;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MetaDataControllerTest extends AbstractIntegrationTest {

    @Test
    public void get() throws Exception {
        given()
            .when()
            .get("mr/api/client/metadata/saml20_sp/1")
            .then()
            .statusCode(SC_OK)
            .body("id", equalTo("1"))
            .body("revision.number", equalTo(0))
            .body("data.entityid", equalTo("Duis ad do"));
    }

    @Test
    public void getNotFound() throws Exception {
        given()
            .when()
            .get("mr/api/client/metadata/saml20_sp/x")
            .then()
            .statusCode(SC_NOT_FOUND);
    }

    @Test
    public void templateSp() throws Exception {
        given()
            .when()
            .get("mr/api/client/template/saml20_sp")
            .then()
            .statusCode(SC_OK)
            .body("type", equalTo("saml20_sp"))
            .body("data.arp.enabled", equalTo(false))
            .body("data.arp.attributes", notNullValue())
            .body("data.metaDataFields", notNullValue())
            .body("data.entityid", isEmptyOrNullString())
            .body("data.state", equalTo("testaccepted"))
            .body("data.allowedEntities.size()", is(0))
            .body("data.disableConsent", isEmptyOrNullString());
    }

    @Test
    public void templateIdp() throws Exception {
        given()
            .when()
            .get("mr/api/client/template/saml20_idp")
            .then()
            .statusCode(SC_OK)
            .body("type", equalTo("saml20_idp"))
            .body("data.allowedall", equalTo(true))
            .body("data.metaDataFields", notNullValue())
            .body("data.entityid", isEmptyOrNullString())
            .body("data.state", equalTo("testaccepted"))
            .body("data.allowedEntities.size()", is(0))
            .body("data.disableConsent.size()", is(0));
    }

    @Test
    public void remove() throws Exception {
        MetaData metaData = metaDataRepository.findById("1", "saml20_sp");
        assertNotNull(metaData);

        given()
            .when()
            .delete("mr/api/client/metadata/saml20_sp/1")
            .then()
            .statusCode(SC_OK);

        metaData = metaDataRepository.findById("1", "saml20_sp");
        assertNull(metaData);
    }


    @Test
    public void configuration() throws Exception {
        given()
            .when()
            .get("mr/api/client/metadata/configuration")
            .then()
            .statusCode(SC_OK)
            .body("size()", is(2))
            .body("title", hasItems("saml20_sp", "saml20_idp"));
    }

    @Test
    public void post() throws Exception {
        String json = fileContent("/json/valid_service_provider.json");
        Map data = objectMapper.readValue(json, Map.class);
        MetaData metaData = new MetaData(EntityType.SP.getType(), data);
        String id = given()
            .when()
            .body(metaData)
            .header("Content-type", "application/json")
            .post("mr/api/client/metadata")
            .then()
            .statusCode(SC_OK)
            .extract().path("id");

        MetaData savedMetaData = metaDataRepository.findById(id, EntityType.SP.getType());
        Revision revision = savedMetaData.getRevision();
        assertEquals("saml2_user.com", revision.getUpdatedBy());
        assertEquals(0, revision.getNumber());
        assertNotNull(revision.getCreated());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void put() throws Exception {
        MetaData metaData = metaDataRepository.findById("1", EntityType.SP.getType());
        Map.class.cast(metaData.getData()).put("entityid", "changed");
        given()
            .when()
            .body(metaData)
            .header("Content-type", "application/json")
            .put("mr/api/client/metadata")
            .then()
            .statusCode(SC_OK)
            .body("revision.number", equalTo(1))
            .body("revision.created", notNullValue())
            .body("revision.updatedBy", equalTo("saml2_user.com"))
            .body("data.entityid", equalTo("changed"));

        List<MetaData> revisions = metaDataRepository.getMongoTemplate().findAll(MetaData.class, "saml20_sp_revision");
        assertEquals(1, revisions.size());

        Revision revision = revisions.get(0).getRevision();
        assertEquals("saml2_user.com", revision.getUpdatedBy());
        assertEquals(0, revision.getNumber());
        assertEquals("1", revision.getParentId());

        given()
            .when()
            .get("mr/api/client/revisions/saml20_sp/1")
            .then()
            .statusCode(SC_OK)
            .body("size()", is(1))
            .body("[0].id", notNullValue())
            .body("[0].revision.number", equalTo(0))
            .body("[0].data.entityid", equalTo("Duis ad do"));
    }

    @Test
    public void autoComplete() throws Exception {
        given()
            .when()
            .queryParam("query", "mock")
            .get("mr/api/client/autocomplete/saml20_sp")
            .then()
            .statusCode(SC_OK)
            .body("size()", is(2))
            .body("'_id'", hasItems("3", "5"))
            .body("data.entityid", hasItems(
                "http://mock-sp",
                "https://serviceregistry.test2.surfconext.nl/simplesaml/module.php/saml/sp/metadata.php/default-sp-2"));
    }

    @Test
    public void search() throws Exception {
        Map<String, Object> searchOptions = new HashMap<>();
        searchOptions.put("metaDataFields.coin:do_not_add_attribute_aliases", "1");
        searchOptions.put("metaDataFields.contacts:3:contactType", "technical");
        searchOptions.put("metaDataFields.NameIDFormat", "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");

        given()
            .when()
            .body(searchOptions)
            .header("Content-type", "application/json")
            .post("mr/api/client/search/saml20_sp")
            .then()
            .statusCode(SC_OK)
            .body("size()", is(2))
            .body("'_id'", hasItems("2", "3"))
            .body("data.entityid", hasItems(
                "http://mock-sp",
                "https://profile.test2.surfconext.nl/authentication/metadata"))
            .body("data.metaDataFields.'name:en'", hasItems(
                "OpenConext Profile",
                "OpenConext Mujina SP"));
    }


    @Test
    public void whiteListing() throws Exception {
        given()
            .when()
            .get("mr/api/client/whiteListing/saml20_sp")
            .then()
            .statusCode(SC_OK)
            .body("size()", is(5))
            .body("data.allowedall", hasItems(true, false));
    }

}