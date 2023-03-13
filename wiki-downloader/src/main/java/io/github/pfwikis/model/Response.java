package io.github.pfwikis.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Response<T> {
    private Query<T> query;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Query<T> {
        private List<T> results;
    }
}
