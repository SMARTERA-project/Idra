package it.eng.idra.dcat.dump;

import it.eng.idra.beans.dcat.DcatProperty;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Shared date output for DCAT resources; never asserts an invalid XSD date. */
final class DcatRdfDates {
  private static final Logger logger = LogManager.getLogger(DcatRdfDates.class);

  private DcatRdfDates() { }

  static void add(Resource target, DcatProperty value) {
    if (value != null) add(target, value.getProperty(), value.getValue());
  }

  static void add(Resource target, Property predicate, String value) {
    if (value == null || value.trim().isEmpty()) return;
    String lexical = value.trim();
    // Java's bracketed ZoneId is not part of the XSD lexical space. Parse it
    // before conversion so the offset and subsecond precision remain intact.
    if (lexical.endsWith("]")) {
      try {
        lexical = ZonedDateTime.parse(lexical).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      } catch (DateTimeParseException ignored) {
        // Preserve unrecognised source metadata below, without an invalid type.
      }
    }
    XSDDatatype type = lexical.contains("T") ? XSDDatatype.XSDdateTime : XSDDatatype.XSDdate;
    if (type.isValid(lexical)) {
      target.addProperty(predicate, lexical, type);
    } else {
      logger.warn("Invalid source date on {} / {}; retaining as a string literal", target, predicate);
      target.addProperty(predicate, value);
    }
  }
}
