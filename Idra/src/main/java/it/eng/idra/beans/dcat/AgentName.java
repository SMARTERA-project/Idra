package it.eng.idra.beans.dcat;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Embeddable;

/** A foaf:name of an agent as stored in dcat_agent_name: the text and its optional language tag. */
@Embeddable
public class AgentName implements Serializable {
  private static final long serialVersionUID = 1L;
  private String value;
  private String language;

  public AgentName() {
  }

  public AgentName(String value, String language) {
    this.value = value;
    this.language = language;
  }

  @Column(name = "name", columnDefinition = "TEXT")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Column(name = "language", length = 64)
  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }
}
