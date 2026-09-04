package dev.ledger.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subcategories")
public class SubcategoryEntity {

  @Id
  @Column(length = 128)
  private String id;

  @Column(name = "category_id", nullable = false, length = 64)
  private String categoryId;

  @Column(name = "display_name", nullable = false, length = 128)
  private String displayName;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected SubcategoryEntity() {}

  public String getId() {
    return id;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
