package dev.ledger.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/** One published CPI level. The primary key is the series and the month it belongs to. */
@Entity
@Table(name = "cpi_observations")
@IdClass(CpiObservationEntity.Key.class)
public class CpiObservationEntity {

  @Id
  @Column(name = "series_code", nullable = false, length = 64)
  private String seriesCode;

  /** Always the first day of the month. */
  @Id
  @Column(nullable = false)
  private LocalDate month;

  @Column(name = "index_value", nullable = false, precision = 19, scale = 6)
  private BigDecimal indexValue;

  @Column(nullable = false, length = 16)
  private String source;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;

  protected CpiObservationEntity() {}

  public CpiObservationEntity(
      String seriesCode, YearMonth month, BigDecimal indexValue, String source) {
    this.seriesCode = seriesCode;
    this.month = month.atDay(1);
    this.indexValue = indexValue;
    this.source = source;
    this.fetchedAt = Instant.now();
  }

  /**
   * Records a value fetched later for a month already cached.
   *
   * @return true when the published figure actually changed, which is TÜİK revising a month rather
   *     than a routine re-fetch (SPEC §6.2)
   */
  public boolean refresh(BigDecimal newValue, String newSource) {
    boolean revised = indexValue.compareTo(newValue) != 0;
    this.indexValue = newValue;
    this.source = newSource;
    this.fetchedAt = Instant.now();
    return revised;
  }

  public String getSeriesCode() {
    return seriesCode;
  }

  public YearMonth getMonth() {
    return YearMonth.from(month);
  }

  public BigDecimal getIndexValue() {
    return indexValue;
  }

  public String getSource() {
    return source;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  /** Composite key: JPA needs a class for it, nothing else does. */
  public static class Key implements Serializable {
    private static final long serialVersionUID = 1L;

    private String seriesCode;
    private LocalDate month;

    public Key() {}

    public Key(String seriesCode, LocalDate month) {
      this.seriesCode = seriesCode;
      this.month = month;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(seriesCode, key.seriesCode)
          && Objects.equals(month, key.month);
    }

    @Override
    public int hashCode() {
      return Objects.hash(seriesCode, month);
    }
  }
}
