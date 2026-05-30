package io.substrait.compliance.api.model.entity;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA converter for persisting string lists in a single database column.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }

        return attribute.stream()
                .map(value -> value == null ? "" : value.replace(",", "\\,"))
                .collect(Collectors.joining(","));
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.stream(dbData.split("(?<!\\\\),", -1))
                .map(value -> value.replace("\\,", ","))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}

// Made with Bob
