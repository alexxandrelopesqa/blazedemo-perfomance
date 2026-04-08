package com.blazedemo.perf;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

final class JsonHelper {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    static {
        MAPPER.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, true);
    }

    private JsonHelper() {}

    static ObjectMapper mapper() {
        return MAPPER;
    }
}
