package it.eng.idra.beans.dcat;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import org.apache.commons.lang3.StringUtils;

@Embeddable
public class DcatKeyword implements Serializable {

  private static final long serialVersionUID = 1L;

  private String value;
  private String language;

  public DcatKeyword() {
  }

  public DcatKeyword(String value, String language) {
    this.value = value;
    this.language = language;
  }

  @Column(name = "keywords")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Column(name = "language")
  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public boolean hasValue() {
    return StringUtils.isNotBlank(value);
  }
}
