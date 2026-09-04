package dev.ledger.imports.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * The smallest RFC 4180 reader that handles a bank export: quoted fields, embedded commas, doubled
 * quotes, and CRLF. A dependency for this would be more code to audit than the code it replaces.
 */
final class CsvReader {

  private CsvReader() {}

  static List<List<String>> read(String csv) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;

    for (int i = 0; i < csv.length(); i++) {
      char c = csv.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        row.add(field.toString());
        field.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
          i++;
        }
        row.add(field.toString());
        field.setLength(0);
        rows.add(row);
        row = new ArrayList<>();
      } else {
        field.append(c);
      }
    }
    if (field.length() > 0 || !row.isEmpty()) {
      row.add(field.toString());
      rows.add(row);
    }
    return rows;
  }
}
