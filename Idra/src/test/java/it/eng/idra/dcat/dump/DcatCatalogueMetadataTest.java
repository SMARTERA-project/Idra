package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.utils.*;
import java.util.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DcatCatalogueMetadataTest {
  @Test void readsAndWritesCatalogueServicesAndRecordsWithOriginalIris() {
    Model input=ModelFactory.createDefaultModel();
    String ttl="@prefix dcat:<http://www.w3.org/ns/dcat#>. @prefix dct:<http://purl.org/dc/terms/>. @prefix foaf:<http://xmlns.com/foaf/0.1/>. @prefix dcatap:<http://data.europa.eu/r5r/>. @prefix ex:<https://example.org/>. ex:catalogue a dcat:Catalog; dct:title \"Catalogue\"; dcatap:applicableLegislation ex:law; dcat:service ex:api; dcat:record ex:record. ex:api a dcat:DataService; dct:title \"API\"; dcat:endpointURL ex:endpoint; dcatap:applicableLegislation ex:law. ex:record a dcat:CatalogRecord; dct:title \"Record\"@en; dct:description \"Metadata\"@en; dct:modified \"2026-01-01\"; foaf:primaryTopic ex:dataset; dct:language <http://publications.europa.eu/resource/authority/language/ENG>.";
    RDFParser.fromString(ttl).lang(Lang.TURTLE).parse(input);
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setHost("https://example.org/catalogue");
    new DcatApDeserializer().readCatalogue(input,node);
    assertEquals("https://example.org/api",node.getService().get(0).getResourceUri());
    assertEquals("https://example.org/record",node.getRecord().get(0).getResourceUri());
    assertEquals("http://publications.europa.eu/resource/authority/language/ENG",node.getRecord().get(0).getLanguages().get(0).getValue());
    Model output=ModelFactory.createDefaultModel();
    DcatRdfMetadata.writeCatalogue(node,output.createResource("https://example.org/catalogue",DCAT.Catalog));
    assertTrue(output.getResource("https://example.org/api").hasProperty(DCAT.endpointURL,output.getResource("https://example.org/endpoint")));
    assertTrue(output.getResource("https://example.org/record").hasProperty(DCTerms.language));
  }
  @Test void legacyExpandedAttributesAndSeriesLanguagesRemainReadable() {
    JSONObject source=new JSONObject().put("id","urn:legacy:dataset")
        .put("https://uri.etsi.org/ngsi-ld/default-context/title",new JSONObject().put("type","Property").put("value","Budget"))
        .put("https://uri.etsi.org/ngsi-ld/default-context/HVDCategory",new JSONObject().put("type","Property").put("value",new JSONArray().put("https://example.org/hvd")));
    DcatDataset dataset=NgsiDcatReader.dataset(source,"999999",id->null);
    assertEquals("Budget",dataset.getTitle().getValue());
    assertEquals("https://example.org/hvd",dataset.getHVDCategory().get(0).getValue());
    DcatJsonMetadata.apply(dataset,new JSONObject("{\"inSeries\":[{\"resourceUri\":\"https://example.org/series\",\"title\":{\"it\":\"Bilanci\",\"en\":\"Budgets\"},\"description\":{\"it\":\"Serie annuale\",\"en\":\"Annual series\"}}]}"));
    assertEquals(2,dataset.getInSeries().get(0).getTitle().size());
    assertTrue(dataset.getInSeries().get(0).getTitle().stream().anyMatch(d->"it".equals(d.getLanguage()) && "Bilanci".equals(d.getTitle())));
  }
}

