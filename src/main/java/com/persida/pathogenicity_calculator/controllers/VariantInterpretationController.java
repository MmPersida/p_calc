package com.persida.pathogenicity_calculator.controllers;

import com.persida.pathogenicity_calculator.RequestAndResponseModels.*;
import com.persida.pathogenicity_calculator.dto.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.persida.pathogenicity_calculator.services.VariantInterpretationService;

import java.util.List;


@RestController
@RequestMapping("/rest/interpretation")
public class VariantInterpretationController {

    private static Logger logger = Logger.getLogger(VariantInterpretationController.class);

    @Autowired
    private VariantInterpretationService variantInterpretationService;

    @PostMapping(value = "/loadInterpretation",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private VariantInterpretationDTO loadInterpretation(@RequestBody VariantInterpretationIDRequest interpretationIDRequest) {
        return variantInterpretationService.loadInterpretation(interpretationIDRequest);
    }

    @PostMapping(value = "/saveNewInterpretation",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private VariantInterpretationSaveResponse saveNewInterpretation(@RequestBody VarInterpSaveUpdateEvidenceDocRequest viSaveEvdUpdateReq) {
        return variantInterpretationService.saveNewInterpretation(viSaveEvdUpdateReq, null);
    }

    @PostMapping(value = "/deleteInterpretation",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private VariantInterpretationSaveResponse deleteInterpretation(@RequestBody VariantInterpretationIDRequest interpretationIDRequest) {
        return variantInterpretationService.deleteInterpretation(interpretationIDRequest);
    }

    @PostMapping(value = "/updateEvidenceDocAndEngine",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private VariantInterpretationSaveResponse updateEvidenceDocAndEngine(@RequestBody VarInterpSaveUpdateEvidenceDocRequest viSaveEvdUpdateReq) {
        return variantInterpretationService.updateEvidenceDocAndEngine(viSaveEvdUpdateReq);
    }

    @PostMapping(value = "/updateCalculatedFinalCall",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private VarInterpUpdateFCResponse updateCalculatedFinalCall(@RequestBody VarInterpUpdateFinalCallRequest viUpdateFCReq) {
        return variantInterpretationService.updateCalculatedFinalCall(viUpdateFCReq);
    }

    @RequestMapping(value = "/getVIBasicDataForCaid/{variantCAID}", method= RequestMethod.GET)
    public List<VIBasicDTO> getVIBasicDataForCaid(@PathVariable String variantCAID){
        if(variantCAID == null || variantCAID.isEmpty()){
            return null;
        }
        return variantInterpretationService.getVIBasicDataForCaid(variantCAID);
    }

    @PostMapping(value = "/searchInterpByCaidEvdcDocEngine",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private  List<VIBasicDTO> searchInterpByCaidEvidenceDoc(@RequestBody VarInterpSaveUpdateEvidenceDocRequest viSaveEvdUpdateReq) {
        return variantInterpretationService.searchInterpByCaidEvidenceDoc(viSaveEvdUpdateReq);
    }

    @PostMapping(value = "/loadViDescription",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private String loadViDescription(@RequestBody VariantInterpretationIDRequest interpretationIDRequest) {
        return variantInterpretationService.loadViDescription(interpretationIDRequest);
    }

    @PostMapping(value = "/saveEditVIDescription",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private String saveEditVIDescription(@RequestBody VariantDescriptionRequest interpretationIDRequest) {
        return variantInterpretationService.saveEditVIDescription(interpretationIDRequest);
    }

    @PostMapping(value = "/saveDeterminedFC",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public VarInterpUpdateFCResponse saveDeterminedFC(@RequestBody VarInterpUpdateFinalCallRequest viUpdateFCReq){
        if(viUpdateFCReq == null){
            return null;
        }
        return variantInterpretationService.saveDeterminedFC(viUpdateFCReq);
    }

    @PostMapping(value = "/generateReportData",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    private ReportDTO generateReportData(@RequestBody VariantInterpretationIDRequest interpretationIDRequest) {
        return variantInterpretationService.generateReportData(interpretationIDRequest);
    }
}
