package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;

import java.io.IOException;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;

@Service
public class ListToJsonArrayConverter<T> implements Converter<List<T>, JsonArray> {

    final ObjectMapper mapper = new ObjectMapper();
    final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    public JsonArray convert(final List<T> sourceList) {
        final JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
        if (sourceList == null) {
            throw new ConverterException("Failed to convert Null List to JsonArray");
        } else {
            sourceList.forEach(object ->
                    jsonArrayBuilder.add(mapObjectToJsonObject(object))
            );
            return jsonArrayBuilder.build();
        }
    }

    @SuppressWarnings("squid:CallToDeprecatedMethod")
    public JsonObject mapObjectToJsonObject(final T object) {
        try {
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            mapper.registerModule(new JSR310Module());
            mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            return this.stringToJsonObjectConverter.convert(this.mapper.writeValueAsString(object));
        } catch (IOException ioexception) {
            throw new ConverterException(String.format("Error while converting object %s to JsonObject", object), ioexception);
        }
    }
}
