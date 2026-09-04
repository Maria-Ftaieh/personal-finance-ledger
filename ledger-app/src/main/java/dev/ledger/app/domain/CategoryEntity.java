package dev.ledger.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity {

  @Id
  @Column(length = 64)
  private String id;

  @Column(name = "display_name", nullable = false, length = 128)
  private String displayName;

  @Column(name = "is_system", nullable = false)
  private boolean system;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected CategoryEntity() {}

  public String getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isSystem() {
    return system;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
