package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import java.io.*;
import java.util.*;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DcatDateSerializationTest {
  @Test void retainsOffsetPrecisionAndUnrecognisedSourceValues() {
    Model model=ModelFactory.createDefaultModel(); Resource r=model.createResource("urn:test:dates");
    DcatRdfDates.add(r,DCTerms.issued,"2026-10-13T15:17:12.123456789+02:00[Europe/Rome]");
    assertEquals("2026-10-13T15:17:12.123456789+02:00",r.getProperty(DCTerms.issued).getString());
    assertTrue(r.getProperty(DCTerms.issued).getLiteral().getDatatype().isValid(r.getProperty(DCTerms.issued).getString()));
    DcatRdfDates.add(r,DCTerms.modified,"2026-02-30");
    assertEquals("2026-02-30",r.getProperty(DCTerms.modified).getString());
    assertEquals(XSDDatatype.XSDstring.getURI(),r.getProperty(DCTerms.modified).getLiteral().getDatatypeURI());
  }

  @Test void bothProfilesPreserveIdentityAndEmitValidDatesWithoutEmptyDistributionDates() throws Exception {
    for (boolean italian : new boolean[]{false, true}) {
      Model input=ModelFactory.createDefaultModel();
      RDFDataMgr.read(input,getClass().getResourceAsStream("/dcat3/series.ttl"),Lang.TURTLE);
      input.getResource("https://example.org/dataset/v2").addProperty(DCAT.distribution,
          input.createResource("https://example.org/distribution",DCAT.Distribution).addProperty(DCAT.accessURL,input.createResource("https://example.org/download")));
      DcatDataset dataset=new DcatApDeserializer().resourceToDataset("999999",input.getResource("https://example.org/dataset/v2"));
      dataset.getReleaseDate().setValue("2026-09-29");
      dataset.getUpdateDate().setValue("2026-09-29T09:50:15.123456Z[Etc/UTC]");
      DcatDistribution distribution=dataset.getDistributions().get(0);
      distribution.getReleaseDate().setValue(""); distribution.getUpdateDate().setValue(" ");
      OdmsCatalogue node=new OdmsCatalogue(); node.setId(999999); node.setHost("https://example.org/catalogue");
      node.setHomepage("https://example.org/"); node.setName("Catalogue"); node.setDescription("Test");
      node.setRegisterDate(java.time.ZonedDateTime.parse("2026-09-29T09:50:15Z[Etc/UTC]"));
      node.setLastUpdateDate(java.time.ZonedDateTime.parse("2026-10-13T15:17:12+02:00[Europe/Rome]"));
      DcatApSerializer.nodeResources=new HashMap<>(); DcatApSerializer.nodeResources.put(node.getId(),node);
      Model output=ModelFactory.createDefaultModel();
      if(italian)DcatApItSerializer.addDatasetToModel(dataset,output);
      else DcatApSerializer.addDatasetToModel(dataset,output);
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();
      RDFDataMgr.write(bytes,output,Lang.RDFXML);
      Model parsed=ModelFactory.createDefaultModel();
      RDFDataMgr.read(parsed,new ByteArrayInputStream(bytes.toByteArray()),Lang.RDFXML);
      Resource subject=parsed.getResource(dataset.getResourceUri());
      assertTrue(subject.hasProperty(RDF.type,DCAT.Dataset));
      assertTrue(subject.hasProperty(ResourceFactory.createProperty(DCAT.NS+"inSeries"),parsed.getResource("https://example.org/series")));
      assertEquals(XSDDatatype.XSDdate.getURI(),subject.getProperty(DCTerms.issued).getLiteral().getDatatypeURI());
      assertEquals("2026-09-29T09:50:15.123456Z",subject.getProperty(DCTerms.modified).getString());
      assertEquals("2026-09-29T09:50:15Z",parsed.getResource(node.getHost()).getProperty(DCTerms.issued).getString());
      assertFalse(parsed.getResource(distribution.getResourceUri()).hasProperty(DCTerms.issued));
      assertFalse(parsed.getResource(distribution.getResourceUri()).hasProperty(DCTerms.modified));
      parsed.listStatements().forEachRemaining(s->{if(s.getObject().isLiteral()) {
        Literal literal=s.getLiteral();
        if(XSDDatatype.XSDdate.getURI().equals(literal.getDatatypeURI()) || XSDDatatype.XSDdateTime.getURI().equals(literal.getDatatypeURI()))
          assertTrue(literal.getDatatype().isValid(literal.getLexicalForm()),s.toString());
      }});
    }
  }
}
