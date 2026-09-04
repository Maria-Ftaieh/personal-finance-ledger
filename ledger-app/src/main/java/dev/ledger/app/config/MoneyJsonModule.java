package dev.ledger.app.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.ledger.core.money.Money;
import java.io.IOException;
import org.springframework.boot.jackson.JsonComponent;

/**
 * Serialises {@link Money} as {@code {"amount":"1234.56","currency":"TRY"}}.
 *
 * <p>SPEC §3.1: the amount crosses the wire as a <b>string</b>. A JSON number is parsed into a
 * JavaScript double by the browser, which is precisely the representation the {@code Money} type
 * exists to avoid; sending {@code 1234.56} as a number would throw away the guarantee at the last
 * step. Registering it as a module rather than annotating each DTO means a {@code Money} added to a
 * response later cannot accidentally go out as a number.
 */
@JsonComponent
public class MoneyJsonModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  public MoneyJsonModule() {
    addSerializer(Money.class, new MoneySerializer());
  }

  static final class MoneySerializer extends JsonSerializer<Money> {
    @Override
    public void serialize(Money value, JsonGenerator json, SerializerProvider serializers)
        throws IOException {
      json.writeStartObject();
      json.writeStringField("amount", value.toString());
      json.writeStringField("currency", value.currency().getCurrencyCode());
      json.writeEndObject();
    }
  }
}
