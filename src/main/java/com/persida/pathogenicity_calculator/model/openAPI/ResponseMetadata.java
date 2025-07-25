package com.persida.pathogenicity_calculator.model.openAPI;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.persida.pathogenicity_calculator.utils.DateUtils;
import lombok.Data;

import java.util.Date;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseMetadata {

    private Rendered rendered;

    public ResponseMetadata(){
        rendered = new Rendered();
        rendered.setBy("https://calculator.test.genome.network/pcalc/srvc");
        rendered.setWhen(DateUtils.dateToStringParser(new Date()));
    }

    @Data
    private class Rendered {
        private String by;
        private String when;
    }
}
