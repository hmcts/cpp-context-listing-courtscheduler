package uk.gov.moj.cpp.courtscheduler.common.converter;

import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;

import java.io.IOException;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ListToJsonArrayConverter<T> implements Converter<List<T>, JsonArray> {

    // NON_NULL inclusion mirrors the project-wide
    // `spring.jackson.default-property-inclusion: non_null` policy so
    // responses don't leak `"field": null` entries that JSON-P consumers
    // (containsKey + getString) cannot parse.
    final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
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

    public JsonObject mapObjectToJsonObject(final T object) {
        try {
            return this.stringToJsonObjectConverter.convert(this.mapper.writeValueAsString(object));
        } catch (IOException ioexception) {
            throw new ConverterException(String.format("Error while converting object %s to JsonObject", object), ioexception);
        }
    }
}
