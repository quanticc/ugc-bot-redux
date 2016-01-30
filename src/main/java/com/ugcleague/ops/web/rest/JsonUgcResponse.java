
package com.ugcleague.ops.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The generic response from the older endpoints of the UGC API.
 */
public class JsonUgcResponse {

    @JsonProperty("COLUMNS")
    private List<String> columns;

    @JsonProperty("DATA")
    private List<List<Object>> data;

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<List<Object>> getData() {
        return data;
    }

    public void setData(List<List<Object>> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "JsonResponse [columns=" + columns + ", data=" + data + "]";
    }

}
