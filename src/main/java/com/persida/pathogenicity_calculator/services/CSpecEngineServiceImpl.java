package com.persida.pathogenicity_calculator.services;

import com.persida.pathogenicity_calculator.RequestAndResponseModels.CSpecEngineRuleSetRequest;
import com.persida.pathogenicity_calculator.RequestAndResponseModels.SortedCSpecEnginesRequest;
import com.persida.pathogenicity_calculator.dto.*;
import com.persida.pathogenicity_calculator.repository.CSpecRuleSetRepository;
import com.persida.pathogenicity_calculator.repository.ConditionRepository;
import com.persida.pathogenicity_calculator.repository.FinalCallRepository;
import com.persida.pathogenicity_calculator.repository.entity.CSpecRuleSet;
import com.persida.pathogenicity_calculator.repository.entity.Condition;
import com.persida.pathogenicity_calculator.repository.entity.FinalCall;
import com.persida.pathogenicity_calculator.repository.entity.Gene;
import com.persida.pathogenicity_calculator.repository.jpa.CSpecRuleSetJPA;
import com.persida.pathogenicity_calculator.utils.EvidenceMapperAndSupport;
import com.persida.pathogenicity_calculator.utils.HTTPSConnector;
import com.persida.pathogenicity_calculator.utils.StackTracePrinter;
import com.persida.pathogenicity_calculator.utils.constants.Constants;
import lombok.Data;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CSpecEngineServiceImpl implements CSpecEngineService{
    static Logger logger = Logger.getLogger(CSpecEngineServiceImpl.class);

    @Value("${cSpecEngineInfoNoIdURL}")
    private String cSpecEngineInfoNoIdURL;

    @Value("${availableVCEPsUrlNewAPI}")
    private String availableVCEPsUrlNewAPI;

    @Value("${cspecRuleSetNoIdUrl}")
    private String cspecRuleSetNoIdUrl;

    @Value("${cspecAssertionsURL}")
    private String cspecAssertionsURL;

    @Autowired
    private CSpecRuleSetRepository cSpecRuleSetRepository;
    @Autowired
    private ConditionRepository conditionRepository;
    @Autowired
    private FinalCallRepository finalCallRepository;

    @Autowired
    private GenesService genesService;

    private JSONParser jsonParser;
    private EvidenceMapperAndSupport esMapperSupport;
    @PostConstruct
    public void initialize(){
        esMapperSupport = new EvidenceMapperAndSupport();
        jsonParser = new JSONParser();
    }


    @Override
    public ArrayList<CSpecEngineDTO> getVCEPsDataByCall(){
        ArrayList<VCEPbasicDTO> releasedVCEPsBasicDataList = getBasicDataOnReleasedVCEPsNewAPI();
        if(releasedVCEPsBasicDataList == null || releasedVCEPsBasicDataList.size() == 0){
            return null;
        }

        ArrayList<CSpecEngineDTO> cSpecEngineDTOList = new ArrayList<CSpecEngineDTO>();
        try {
            CSpecEngineDTO cSpecEngineDTO = null;
            boolean enabled = true;
            for(VCEPbasicDTO releasedVCEP : releasedVCEPsBasicDataList){

                String engineInfoResponse = getcSpecEngineRelatedInfo(releasedVCEP.getEngineId());
                if (engineInfoResponse == null) {
                    continue;
                }

                //we need the label that was the engine summary
                JSONObject engineInfObj = (JSONObject) jsonParser.parse(engineInfoResponse);
                String engineSummary = String.valueOf(engineInfObj.get("label"));

                //get the combined rule-sets and criteria codes
                MainRulesAndCriteriaCodes mainRulesAndCriteriaCodes = null;
                mainRulesAndCriteriaCodes = getMainRulesAndCriteriaCodesFromRuleSetInfo(releasedVCEP.getRuleSetId());
                if (mainRulesAndCriteriaCodes == null) {
                    mainRulesAndCriteriaCodes = getParentVCEPMainRulesAndCriteriaCodes(releasedVCEP.getEngineId());
                    if(mainRulesAndCriteriaCodes == null){
                        continue;
                    }
                }

                //if the main rules (Guideline Combining Criteria) are not present just mark it as disabled to be sure
                String ruleSetJSONStr = mainRulesAndCriteriaCodes.getMainRules().toJSONString();
                if (ruleSetJSONStr == null || ruleSetJSONStr.equals("")) {
                    enabled = false;
                }

                //set all basic data including main rule set
                cSpecEngineDTO = new CSpecEngineDTO(releasedVCEP.getEngineId(), engineSummary, releasedVCEP.getOrganizationName(), releasedVCEP.getOrganizationLink(),
                        releasedVCEP.getRuleSetId(), releasedVCEP.getRuleSetURL(), releasedVCEP.getGenes(), ruleSetJSONStr, enabled);

                //set criteriaCodes - evidence tag info for this engine (VCEP), at this point this can be null
                JSONArray criteriaCodes = mainRulesAndCriteriaCodes.getCriteriaCodes();
                String criteriaCodesJsonStr = processCriteriaCodes(criteriaCodes, releasedVCEP.getRuleSetId());
                if (criteriaCodesJsonStr != null && !criteriaCodesJsonStr.equals("")) {
                    cSpecEngineDTO.setCriteriaCodesJSONStr(criteriaCodesJsonStr);
                }

                cSpecEngineDTOList.add(cSpecEngineDTO);
            }
        }catch(Exception e){
            logger.info(StackTracePrinter.printStackTrace(e));
        }
        logger.info("Received info on "+cSpecEngineDTOList.size()+" valid (non legacy) CSpecEgines!");
        return cSpecEngineDTOList;
    }

    private ArrayList<VCEPbasicDTO> getBasicDataOnReleasedVCEPsNewAPI(){
        ArrayList<VCEPbasicDTO> releasedVCEPsBasicInfoList = null;
        String vcepsListResponse = getAListOfVCEPsDataFromNewAPI();
        if(vcepsListResponse == null || vcepsListResponse.isEmpty()){
            return null;
        }

        releasedVCEPsBasicInfoList = new ArrayList<VCEPbasicDTO>();
        int c = 0;
        try {
            VCEPbasicDTO vcepBasicObj = null;
            JSONObject obj = (JSONObject) jsonParser.parse(vcepsListResponse);
            JSONArray dataArray = (JSONArray) obj.get("data");
            add_GN001_Data(dataArray);
            if(dataArray != null && dataArray.size() > 0){
                main:
                for(Object dataObj : dataArray){
                    JSONObject vcepObj =  (JSONObject) dataObj;
                    String status = String.valueOf(vcepObj.get("status"));
                    if(status != null && status.equals("Released")) {
                        c++;
                        String engineID = getLastParamOfUrl(String.valueOf(vcepObj.get("@id")));

                        //organization
                        JSONObject affiliationObj = null;
                        if(vcepObj.get("affiliation") == null){
                            continue main;
                        }
                        affiliationObj = (JSONObject) vcepObj.get("affiliation");
                        String organizationName = String.valueOf(affiliationObj.get("label"));
                        String organizationLink = String.valueOf(affiliationObj.get("url"));

                        //ruleSets
                        JSONArray ruseSetsArray = null;
                        if(vcepObj.get("ruleSets") == null){
                            continue main;
                        }
                        ruseSetsArray = (JSONArray) vcepObj.get("ruleSets");
                        if(ruseSetsArray.size() == 0){
                            continue main;
                        }
                        JSONObject ruleSetObj = (JSONObject) ruseSetsArray.get(0);
                        String ruleSetURL = String.valueOf(ruleSetObj.get("@id"));
                        int ruleSetId = Integer.parseInt(getLastParamOfUrl(ruleSetURL));

                        //initialize object and set basic data
                        vcepBasicObj = new VCEPbasicDTO();
                        vcepBasicObj.setEngineId(engineID);
                        vcepBasicObj.setOrganizationName(organizationName);
                        vcepBasicObj.setOrganizationLink(organizationLink);
                        vcepBasicObj.setRuleSetId(ruleSetId);
                        vcepBasicObj.setRuleSetURL(ruleSetURL);

                        //genes
                        JSONArray genesList = (JSONArray) ruleSetObj.get("genes");
                        if(genesList != null && genesList.size() > 0){
                            ArrayList<EngineRelatedGeneDTO> engineRelatedGeneDTOList = processEngineRelatedGenes(genesList);
                            if (engineRelatedGeneDTOList != null && engineRelatedGeneDTOList.size() > 0) {
                                for (EngineRelatedGeneDTO e : engineRelatedGeneDTOList) {
                                    vcepBasicObj.addGenes(e);
                                }
                            }
                        }

                        releasedVCEPsBasicInfoList.add(vcepBasicObj);
                    }
                }
            }
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
        }

        logger.info("Finished collecting released VCEPs data (new API), number of gathered: "+c);

        return releasedVCEPsBasicInfoList;
    }

    private MainRulesAndCriteriaCodes getMainRulesAndCriteriaCodesFromRuleSetInfo(int ruleSetID){
        String jsonStrResponse = getcSpecEngineRuleSet(ruleSetID);
        if(jsonStrResponse == null || jsonStrResponse.equals("")){
            return null;
        }
        return extractMainRulesAndCriteriaCodes(jsonStrResponse);
    }

    private MainRulesAndCriteriaCodes getParentVCEPMainRulesAndCriteriaCodes(String engineId){
        String apiURL = "https://cspec.genome.network/cspec/SequenceVariantInterpretation/id/"+engineId+"?fields=ldFor.SequenceVariantInterpretation%5B0%5D.ldhIri";

        HashMap<String,String> httpProperties = new HashMap<String,String>();
        httpProperties.put(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_APP_JSON);

        HTTPSConnector https = new HTTPSConnector();

        String ldhIri = null;
        String jsonResponse = null;
        jsonResponse = https.sendHttpsRequest(apiURL, Constants.HTTP_GET, null, httpProperties);
        try {
            JSONObject obj = (JSONObject) jsonParser.parse(jsonResponse);
            JSONObject dataObj = (JSONObject) obj.get("data");
            if(dataObj == null){
                return null;
            }
            JSONObject ldForObj = (JSONObject) dataObj.get("ldFor");
            if(ldForObj == null){
                return null;
            }
            JSONArray sviArray = (JSONArray) ldForObj.get("SequenceVariantInterpretation");
            if(sviArray == null || sviArray.size() == 0){
                return null;
            }

            JSONObject sviObj = (JSONObject) sviArray.get(0);
            ldhIri = String.valueOf(sviObj.get("ldhIri"));
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
            return null;
        }

        if(ldhIri == null){
            return null;
        }

        jsonResponse = https.sendHttpsRequest(ldhIri, Constants.HTTP_GET, null, httpProperties);
        try {
            //main rules
            JSONObject obj = (JSONObject) jsonParser.parse(jsonResponse);
            JSONObject data = (JSONObject) obj.get("data");
            if(data == null){
                return null;
            }
            JSONObject ld = (JSONObject) data.get("ld");
            if(ld == null){
                return null;
            }
            JSONArray criteriaCode = (JSONArray) ld.get("CriteriaCode");
            if(criteriaCode == null || criteriaCode.size() == 0){
                return null;
            }
            JSONArray ruleSet = (JSONArray) ld.get("RuleSet");
            if(ruleSet == null || ruleSet.size() == 0){
                return null;
            }

            //CriteriaCode's - applicable evidence tags
            JSONObject entContent = (JSONObject) ((JSONObject) ruleSet.get(0)).get("entContent");
            if(entContent == null){
                return null;
            }
            JSONObject rules = (JSONObject) entContent.get("rules");
            if(rules == null){
                return null;
            }
            JSONArray mainRules = (JSONArray) rules.get("mainRules");
            if(mainRules == null || mainRules.size() == 0){
                return null;
            }

            return new MainRulesAndCriteriaCodes(mainRules, criteriaCode);
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
        }
        return null;
    }

    private MainRulesAndCriteriaCodes extractMainRulesAndCriteriaCodes(String jsonStrResponse){
        if(jsonStrResponse == null || jsonStrResponse.equals("")){
            return null;
        }

        try {
            //main rules
            JSONObject obj = (JSONObject) jsonParser.parse(jsonStrResponse);
            JSONObject data = (JSONObject) obj.get("data");
            if(data == null){
                return null;
            }
            JSONObject entContent = (JSONObject) data.get("entContent");
            if(entContent == null){
                return null;
            }
            JSONObject rules = (JSONObject) entContent.get("rules");
            if(rules == null){
                return null;
            }
            JSONArray mainRules = (JSONArray) rules.get("mainRules");
            if(mainRules == null || mainRules.size() == 0){
                return null;
            }

            //CriteriaCode's - applicable evidence tags
            JSONObject ld = (JSONObject) data.get("ld");
            if(ld == null){
                return null;
            }
            JSONArray criteriaCode = (JSONArray) ld.get("CriteriaCode");
            if(criteriaCode == null){
                return null;
            }

            return new MainRulesAndCriteriaCodes(mainRules, criteriaCode);
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
        }
        return null;
    }

    private String processCriteriaCodes(JSONArray criteriaCodes, int ruleSetId){
        if(criteriaCodes == null || criteriaCodes.size() == 0){
            return null;
        }
        JSONArray jsonArray = new JSONArray();
        //CriteriaCode criteriaCode = null;
        JSONObject jsonObj = null;

        for(Object o : criteriaCodes){
            JSONObject criteriaCodeObj = (JSONObject) o;
            JSONObject entContent = (JSONObject) criteriaCodeObj.get("entContent");
            if(entContent == null){
                continue;
            }
            jsonObj = new JSONObject();

            String name = String.valueOf(entContent.get("label"));
            jsonObj.put("name", name);

            boolean applicable = true;
            String applicability = String.valueOf(entContent.get("applicability"));
            if(applicability != null && !applicability.equals("") && !applicability.equals("null")){
                if((applicability != null && applicability.equals("Applicable"))){
                    applicable = true;
                }else if(applicability.startsWith("Not Applicable")){
                    applicable = false;
                }else if(applicability.startsWith("Not applicable")){
                    //Not applicable
                    applicable = false;
                }else{
                    applicable = false;
                    logger.info("NOTE: Unknown applicability value for criteria code \""+applicability+"\" in ruleSet: "+ruleSetId);
                }
            }
            jsonObj.put("applicable", applicable);

            JSONArray strengthDescriptor = (JSONArray) entContent.get("strengthDescriptor");
            if(strengthDescriptor != null && strengthDescriptor.size() > 0){
                JSONObject applicableTags = new JSONObject();

                JSONObject tagData = null;
                for(Object sdObj : strengthDescriptor){
                    tagData = new JSONObject();
                    JSONObject sd = (JSONObject) sdObj;

                    String tagText =  String.valueOf(sd.get("text"));
                    String tagInstructions = String.valueOf(sd.get("instructionsToUse"));

                    if((tagText == null || tagText.equals("")) &&
                            (tagInstructions == null || tagInstructions.equals(""))){
                        continue;
                    }

                    String tagStrength = String.valueOf(sd.get("strength"));
                    String tagApplicability =  String.valueOf(sd.get("applicability"));
                    String tagStatus =  String.valueOf(sd.get("status"));
                    tagData.put("applicability", tagApplicability);
                    tagData.put("text", tagText);
                    tagData.put("instructions", tagInstructions);
                    tagData.put("status", tagStatus);
                    applicableTags.put(tagStrength, tagData);
                }

                jsonObj.put("applicableTags", applicableTags);
            }

            String comment = String.valueOf(entContent.get("additionalComments"));
            if(comment == null || comment.equals("")){
                comment = String.valueOf(entContent.get("originalACMGSummary"));
            }
            jsonObj.put("comment", comment);

            String infoURL = String.valueOf(criteriaCodeObj.get("ldhIri"));
            jsonObj.put("infoURL", infoURL);

            JSONArray genes = (JSONArray) entContent.get("gene");
            if(genes != null && genes.size() > 0){
                jsonObj.put("genes", genes);
            }

            JSONArray disease = (JSONArray) entContent.get("disease");
            if(disease != null && disease.size() > 0){
                jsonObj.put("diseases", disease);
            }

            jsonArray.add(jsonObj);
        }

        if(jsonArray.size() > 0){
            return jsonArray.toJSONString();
        }
        return null;
    }

    private ArrayList<EngineRelatedGeneDTO> processEngineRelatedGenes(JSONArray genes){
        if(genes == null || genes.size() == 0){
            return null;
        }
        ArrayList<EngineRelatedGeneDTO> list = new ArrayList<EngineRelatedGeneDTO>();

        for(Object gene : genes){
            JSONObject geneObj = (JSONObject) gene;
            String geneName = String.valueOf(geneObj.get("label"));

            String[] hgncAndNcbiIds = genesService.getGeneHGNCandNCBIids(geneName);
            EngineRelatedGeneDTO eRelatedGene = new EngineRelatedGeneDTO(geneName, hgncAndNcbiIds[0], hgncAndNcbiIds[1]);
            JSONArray diseases = (JSONArray) geneObj.get("diseases");
            if(diseases != null && diseases.size() != 0){
                for(Object disease : diseases){
                    JSONObject diseaseObj = (JSONObject) disease;
                    String diseaseMongoId = String.valueOf(diseaseObj.get("label"));
                    Condition c = conditionRepository.getConditionById(diseaseMongoId);
                    if(c != null){
                        eRelatedGene.addConditions(new ConditionsTermAndIdDTO(diseaseMongoId, c.getTerm()));
                    }
                }
            }
            list.add(eRelatedGene);
        }
        return list;
    }

    private String getLastParamOfUrl(String urlString) {
        URI url = null;

        try{
            url = new URI(urlString);
            if(url == null){
                return null;
            }
            String[] segments = url.getPath().split("/");
            String strParam = segments[segments.length - 1];
            if(strParam != null && !strParam.isEmpty()){
                return strParam;
            }
        }catch(Exception e){
            logger.info(StackTracePrinter.printStackTrace(e));
        }
        return null;
    }

    @Override
    public CSpecEngineDTO getCSpecEngineInfo(String cspecengineId){
        CSpecEngineDTO cspecengineDTO = null;
        CSpecRuleSet cspec = cSpecRuleSetRepository.getCSpecRuleSetById(cspecengineId);
        if(cspec == null){
            return null;
        }
        Set<EngineRelatedGeneDTO> erGenesSet = processRelatedGenes(cspec);
        cspecengineDTO = new CSpecEngineDTO(cspec.getEngineId(), cspec.getEngineSummary(),
                cspec.getOrganizationName(), cspec.getOrganizationLink(),
                cspec.getRuleSetId(), cspec.getRuleSetURL(), erGenesSet, cspec.getEnabled());
        return cspecengineDTO;
    }

    @Override
    public ArrayList<CSpecEngineDTO> getVCEPsInfoByName(String vcepNamePartial){
        ArrayList<CSpecEngineDTO> cspecenginesList = null;
        List<CSpecRuleSet> allEnginesList = null;
        if(!vcepNamePartial.isEmpty() && vcepNamePartial.equals("all_data")){
            allEnginesList = cSpecRuleSetRepository.getAllEnabledCSpecEnginesInfo();
        }else if(!vcepNamePartial.isEmpty() && vcepNamePartial.length() >= 4){
            allEnginesList = cSpecRuleSetRepository.getAllEnabledCSpecEnginesInfoByNameLike(vcepNamePartial);
        }

        if(allEnginesList == null || allEnginesList.size() == 0){
            return cspecenginesList;
        }

        cspecenginesList = new ArrayList<CSpecEngineDTO>();

        CSpecEngineDTO cspecengineDTO = null;
        for(CSpecRuleSet cspec: allEnginesList){
            Set<EngineRelatedGeneDTO> erGenesSet = processRelatedGenes(cspec);
            cspecengineDTO = new CSpecEngineDTO(cspec.getEngineId(), cspec.getEngineSummary(),
                    cspec.getOrganizationName(), cspec.getOrganizationLink(),
                    cspec.getRuleSetId(), cspec.getRuleSetURL(), erGenesSet, cspec.getEnabled());
            cspecenginesList.add(cspecengineDTO);
        }
        return cspecenginesList;
    }

    @Override
    public AssertionsDTO getCSpecRuleSet(CSpecEngineRuleSetRequest cSpecEngineRuleSetRequest){
        if(cSpecEngineRuleSetRequest.getEvidenceMap() == null){
            cSpecEngineRuleSetRequest.setEvidenceMap(new HashMap<String,Integer>());
        }
        CSpecRuleSet cspec = cSpecRuleSetRepository.getCSpecRuleSetById( cSpecEngineRuleSetRequest.getCspecengineId());
        if(cspec == null || cspec.getRuleSetJSONStr() == null || cspec.getRuleSetJSONStr().equals("")){
            return null;
        }

        try{
            JSONArray arrayObj = (JSONArray) jsonParser.parse(cspec.getRuleSetJSONStr());
            return determineAssertions(arrayObj, cSpecEngineRuleSetRequest.getEvidenceMap());
        }catch(Exception e){
            System.out.println(StackTracePrinter.printStackTrace(e));
        }
        return null;
    }

    @Override
    public SortedCSpecEnginesDTO getSortedAndEnabledCSpecEngines(SortedCSpecEnginesRequest sortedCSpecEnginesRequest){
        SortedCSpecEnginesDTO sCseDTO = null;

        List<CSpecRuleSetJPA> allEnginesList = cSpecRuleSetRepository.getAllEnabledCSpecEnginesBasicInfo();
        if(allEnginesList == null || allEnginesList.size() == 0){
            return sCseDTO;
        }

        //get engines that are linked to this gene or condition
        HashMap<String, CSpecRuleSetJPA> sortedEnginesMap = null;
        String conditionId = null;
        if(sortedCSpecEnginesRequest.getCondition() != null && sortedCSpecEnginesRequest.getGene() != null){
            conditionId = conditionRepository.getConditionIdFromName(sortedCSpecEnginesRequest.getCondition());
            List<CSpecRuleSetJPA> sortedEnginesList = cSpecRuleSetRepository.getSortedAndEnabledCSpecEngines(sortedCSpecEnginesRequest.getGene(), conditionId);
            if(sortedEnginesList != null && sortedEnginesList.size() > 0){
                sortedEnginesMap = new HashMap<String, CSpecRuleSetJPA>();
                for(CSpecRuleSetJPA e : sortedEnginesList){
                    sortedEnginesMap.put(e.getEngineId(), e);
                }
            }
        }

        sCseDTO = new SortedCSpecEnginesDTO();
        //go through all the engines available
        for(CSpecRuleSetJPA e : allEnginesList){
            CSpecEngineDTO cseDTO = new CSpecEngineDTO(e.getEngineId(), e.getEngineSummary(),
                                                        e.getOrganization(), e.getOrganizationLink());

            //if no engines that are linked to the specified gene or condition exist then load all of them in sCseDTO and be done
            if(sortedEnginesMap == null || sortedEnginesMap.size() == 0){
                //this is a way not to check other cases in vain from the beginning
                sCseDTO.addToOthersList(cseDTO);
                continue;
            }

            //if engines that are linked to the specified gene or condition exist, then first try to load those in separate lists then try "the other ones" at the very end
            CSpecRuleSetJPA tempEngine = sortedEnginesMap.get(e.getEngineId());
            if(tempEngine != null){
                if(tempEngine.getGeneId() != null && sortedCSpecEnginesRequest.getGene().equals(tempEngine.getGeneId()) &&
                        tempEngine.getConditionId() != null && conditionId.equals(tempEngine.getConditionId())){
                    sCseDTO.addToGeneAndConditionList(cseDTO);
                    continue;
                }
                if(sortedCSpecEnginesRequest.getGene().equals(tempEngine.getGeneId())){
                    sCseDTO.addToGeneList(cseDTO);
                    continue;
                }
                if(conditionId.equals(tempEngine.getConditionId())){
                    sCseDTO.addToConditionList(cseDTO);
                    continue;
                }
            }

            sCseDTO.addToOthersList(cseDTO);
        }

        return sCseDTO;
    }

    private Set<EngineRelatedGeneDTO> processRelatedGenes(CSpecRuleSet e){
        EngineRelatedGeneDTO erGene = null;
        Set<EngineRelatedGeneDTO> erGenesSet = null;
        if(e.getGenes() != null && e.getGenes().size() > 0){
            erGenesSet = new HashSet<EngineRelatedGeneDTO>();
            Set<Gene> gList = e.getGenes();
            for(Gene g : gList){
                if(g.getConditions() != null && g.getConditions().size() > 0){
                    Set<Condition> cSet = g.getConditions();
                    ArrayList<ConditionsTermAndIdDTO> condDTOList = new ArrayList<ConditionsTermAndIdDTO>();
                    for(Condition c : cSet){
                        condDTOList.add(new ConditionsTermAndIdDTO(c.getCondition_id(), c.getTerm()));
                    }
                    erGene = new EngineRelatedGeneDTO(g.getGeneId(), g.getHgncId(), g.getNcbiId(), condDTOList);
                }else{
                    erGene = new EngineRelatedGeneDTO(g.getGeneId(), g.getHgncId(), g.getNcbiId());
                }
                if(erGene != null){
                    erGenesSet.add(erGene);
                }
            }
        }
        return erGenesSet;
    }

    @Override
    public FinalCallDTO callScpecEngine(CSpecEngineRuleSetRequest cSpecEngineRuleSetRequest){
        Integer ruleSetId = null;
        if(cSpecEngineRuleSetRequest.getRulesetId() != null && !cSpecEngineRuleSetRequest.getRulesetId().equals("")){
            ruleSetId = cSpecEngineRuleSetRequest.getRulesetId();
        }else{
            CSpecRuleSet cspec = cSpecRuleSetRepository.getCSpecRuleSetById(cSpecEngineRuleSetRequest.getCspecengineId());
            if(cspec != null){
                ruleSetId = cspec.getRuleSetId();
            }
        }

        if(ruleSetId == null){
            logger.error("Unabale to get rulesetId!");
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put("cspecRuleSetUrl",cspecRuleSetNoIdUrl+ruleSetId);
        obj.put("evidence",cSpecEngineRuleSetRequest.getEvidenceMap());
        String jsonData = obj.toJSONString();

        HashMap<String,String> httpProperties = new HashMap<String,String>();
        httpProperties.put(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_APP_JSON);

        HTTPSConnector https = new HTTPSConnector();
        String response = https.sendHttpsRequest(cspecAssertionsURL, Constants.HTTP_POST, jsonData, httpProperties);

        FinalCallDTO fcDTO = null;
        String finalCallVal = null;
        try{
            JSONObject responseObj = (JSONObject) jsonParser.parse(response);
            JSONObject dataObj = (JSONObject) responseObj.get("data");
            if(dataObj != null){
                finalCallVal = String.valueOf(dataObj.get("finalCall"));
                if(finalCallVal != null && !finalCallVal.isEmpty()){
                    FinalCall fc =finalCallRepository.getFinalCallByName(finalCallVal);
                    fcDTO = new FinalCallDTO(fc.getId(), fc.getTerm());
                }
            }
        }catch(Exception e){
            logger.info("Unknown Final Call from CSPec: "+finalCallVal);
            logger.error(StackTracePrinter.printStackTrace(e));
        }
        return fcDTO;
    }

    @Override
    public String getRuleSetCriteriaCodes(String cspecengineId){
        CSpecRuleSet cspec = cSpecRuleSetRepository.getCSpecRuleSetById(cspecengineId);
        if(cspec == null || cspec.getCriteriaCodesJSONStr() == null || cspec.getCriteriaCodesJSONStr().equals("")){
            return null;
        }

        return cspec.getCriteriaCodesJSONStr();
    }

    @Override
    public HashMap<String,String> getEvidenceCommentByEvdNameList(String cspecengineId, List<EvidenceDTO> evidenceList){
        if(evidenceList == null || evidenceList.size() == 0){
            return null;
        }
        String rsCriteriaCodes = this.getRuleSetCriteriaCodes(cspecengineId);

        if(rsCriteriaCodes == null || rsCriteriaCodes.equals("")){
            return null;
        }

        HashMap<String,String> evdTypeAndCommentMap = new HashMap<String,String>();
        try {
            JSONArray rsCriteriaCodesJSON = (JSONArray) jsonParser.parse(rsCriteriaCodes);
            if(rsCriteriaCodesJSON == null || rsCriteriaCodesJSON.size() == 0){
                return null;
            }

            for(EvidenceDTO evdDTO : evidenceList){
                String endType = evdDTO.getType(); //example: BP1, BS2, PP1 etc. basic type, no modifiers

                /*
                Basically, if the evd. tag has a modifier get that first, if not, get the actual sub-type
                BS2-Supporting -> Supporting  OR  BS2 -> Strong
                This is the same thing that happens on the front end side for these purposes.
                */
                String evdModifier = null;
                if(evdDTO.getModifier() != null && !evdDTO.getModifier().equals("")){
                    evdModifier = evdDTO.getModifier();
                }else{
                    evdModifier = esMapperSupport.getEvdTagData(endType).getModifier();
                }

                for(Object obj : rsCriteriaCodesJSON) {
                    JSONObject rsCriteriaCode = (JSONObject) obj;
                    String evidenceTagName = (String.valueOf(rsCriteriaCode.get("name"))).toUpperCase();

                    if (endType.equals(evidenceTagName)){
                        String comment = null;
                        if(rsCriteriaCode.get("applicableTags") != null){
                            JSONObject applicableTags = (JSONObject) rsCriteriaCode.get("applicableTags");
                            if(evdModifier != null && applicableTags != null && applicableTags.get(evdModifier) != null){
                                JSONObject applicableTag = (JSONObject) applicableTags.get(evdModifier);
                                if(applicableTag.get("text") != null){
                                    comment = String.valueOf(applicableTag.get("text"));
                                }else if(applicableTag.get("instructions") != null){
                                    comment = String.valueOf(applicableTag.get("instructions"));
                                }
                            }else{
                                comment = String.valueOf(rsCriteriaCode.get("comment"));
                            }
                        }

                        if(comment != null){
                            evdTypeAndCommentMap.put(endType, comment);
                        }
                        break;
                    }
                }
            }
        }catch(Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
        }

        return evdTypeAndCommentMap;
    }

    private String getAListOfVCEPsDataFromNewAPI(){
        HashMap<String,String> httpProperties = new HashMap<String,String>();
        httpProperties.put(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_APP_JSON);

        HTTPSConnector https = new HTTPSConnector();
        String response = https.sendHttpsRequest(availableVCEPsUrlNewAPI, Constants.HTTP_GET, null, httpProperties);
        return convertResposneToUTF8(response);
    }

    private String getcSpecEngineRelatedInfo(String cSpecEngineEntID){
        HashMap<String,String> httpProperties = new HashMap<String,String>();
        httpProperties.put(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_APP_JSON);

        String cSpecEngineInfoWithIdURL = cSpecEngineInfoNoIdURL + cSpecEngineEntID;

        HTTPSConnector https = new HTTPSConnector();
        String response = https.sendHttpsRequest(cSpecEngineInfoWithIdURL, Constants.HTTP_GET, null, httpProperties);
        return convertResposneToUTF8(response);
    }

    private String getcSpecEngineRuleSet(int ruseSetId){
        HashMap<String,String> httpProperties = new HashMap<String,String>();
        httpProperties.put(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_APP_JSON);

        String cspecRuleSetWithIdUrl = cspecRuleSetNoIdUrl+ruseSetId;

        HTTPSConnector https = new HTTPSConnector();
        String response = https.sendHttpsRequest(cspecRuleSetWithIdUrl, Constants.HTTP_GET, null, httpProperties);
        return convertResposneToUTF8(response);
    }

    private String convertResposneToUTF8(String reponseString){
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(reponseString);
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private AssertionsDTO determineAssertions(JSONArray arrayObj, Map<String,Integer> evidenceMap){
        AssertionsDTO assertionsDTO = null;

        if(arrayObj == null || arrayObj.size() == 0){
            logger.error("Error: Unable to get CSpec Engine rule set from the response, entContent property is null!");
            return null;
        }
        assertionsDTO = new AssertionsDTO();

        for(Object obj : arrayObj){
            JSONObject ruleObj = (JSONObject) obj;
            String currentInference = String.valueOf(ruleObj.get("inference"));

            //individual rules
            String extractedCondLabels = "";
            String extractedEvidenceTableColumnMarkers = "";
            int totalCondVal = 0;

            JSONArray conditions = (JSONArray) ruleObj.get("conditions");
            int passedConditions = conditions.size();
            int j = passedConditions;
            for(int k=0; k<j; k++){
                JSONObject condition = (JSONObject) conditions.get(k);

                String conditionValue = String.valueOf(condition.get("condition"));
                int currentCondNumValue = getValueForCondition(conditionValue);
                String partitionPathVal = String.valueOf(condition.get("partitionPath"));
                boolean evidenceFailed = true;

                if(evidenceMap != null){
                    //evidenceSet contains all the currently added evidences
                    if(evidenceMap.get(partitionPathVal) != null){
                        int totalNumOfEvidenceAdded = evidenceMap.get(partitionPathVal);
                        if(totalNumOfEvidenceAdded < currentCondNumValue){
                            //there aren't enough added evidences to satisfy this condition, therefor the condition failed to pass
                            passedConditions--;
                        }else{
                            evidenceFailed = false;
                        }
                    }else{
                        passedConditions--;
                    }
                }else{
                    passedConditions--;
                }

                if(evidenceFailed){
                    totalCondVal = totalCondVal + currentCondNumValue;
                }

                extractedEvidenceTableColumnMarkers = extractedEvidenceTableColumnMarkers + determineEvidenceTableColumnMarkerValue(partitionPathVal)+"_";

                if(k == (j-1)){
                    extractedCondLabels = extractedCondLabels + partitionPathVal+""+conditionValue;
                }else{
                    extractedCondLabels = extractedCondLabels + partitionPathVal+""+conditionValue+" & ";
                }
            }

            if(totalCondVal > 2){
                continue;
            }

            RuleConditionDTO rcTDO = null;
            if(evidenceMap != null){
                if(passedConditions == j){
                    if(assertionsDTO.getReachedAssertions().get(currentInference) == null){
                        assertionsDTO.addToReachedAssertions(currentInference, new ArrayList<RuleConditionDTO>());
                    }
                    rcTDO = new RuleConditionDTO(extractedCondLabels, extractedEvidenceTableColumnMarkers);
                    assertionsDTO.addReachedAssertionConditionForKey(currentInference,rcTDO);
                }else{
                    if(assertionsDTO.getFailedAssertions().get(currentInference) == null){
                        assertionsDTO.addToFailedAssertions(currentInference, new ArrayList<RuleConditionDTO>());
                    }
                    rcTDO = new RuleConditionDTO(extractedCondLabels, extractedEvidenceTableColumnMarkers, totalCondVal);
                    assertionsDTO.addFailedAssertionConditionForKey(currentInference,rcTDO);
                }
            }else{
                if(passedConditions < j){
                    if(assertionsDTO.getFailedAssertions().get(currentInference) == null){
                        assertionsDTO.addToFailedAssertions(currentInference, new ArrayList<RuleConditionDTO>());
                    }
                    rcTDO = new RuleConditionDTO(extractedCondLabels, extractedEvidenceTableColumnMarkers, totalCondVal);
                    assertionsDTO.addFailedAssertionConditionForKey(currentInference,rcTDO);
                }
            }
        }

        sortFailedRuleSet(assertionsDTO.getFailedAssertions());

        return assertionsDTO;
    }

    private int getValueForCondition(String cValue){
        int baseVal = 0;
        //cValue, example: ==1, >=1, ==2, >=2, ==5, >=5....

        Pattern pattern = Pattern.compile("^(==|>=)[1-9][1-9]*");
        Matcher matcher = pattern.matcher(cValue);
        if(matcher.find()){
            String value = cValue.substring(2, cValue.length());
            if(value !=null && !value.equals("")){
                try{
                    baseVal = Integer.parseInt(value);
                }catch(Exception e){
                    logger.error(StackTracePrinter.printStackTrace(e));
                }
            }
        }
        return baseVal;
    }

    private String determineEvidenceTableColumnMarkerValue(String partitionPathVal){
        String[] partitionPathValArray = partitionPathVal.split("\\.");
        String pathBasic = partitionPathValArray[0];
        String pathDetail = partitionPathValArray[1];

        String markerValue = "";

        if(pathBasic.equals(Constants.TYPE_BENIGN)) {
            markerValue += '1';

            if (pathDetail.equals(Constants.MODIFIER_SUPPORTING)) {
                markerValue += "1";
            } else if (pathDetail.equals(Constants.MODIFIER_STRONG)) {
                markerValue += "2";
            } else if(pathDetail.equals(Constants.MODIFIER_STAND_ALONE)) {
                markerValue += "3";
            }
            /*
            else if (pathDetail.equals(Constants.MODIFIER_MODERATE)) {
                markerValue += "?"; //check with the table layout
            } else if(pathDetail.equals(Constants.MODIFIER_VERY_STRONG)){
                markerValue += "?"; //check with the table layout
            }*/
        }else if(pathBasic.equals(Constants.TYPE_PATHOGENIC)){
            markerValue += "2";

            if(pathDetail.equals(Constants.MODIFIER_SUPPORTING)){
                markerValue += "1";
            }else if(pathDetail.equals(Constants.MODIFIER_MODERATE)){
                markerValue += "2";
            }else if(pathDetail.equals(Constants.MODIFIER_STRONG)){
                markerValue += "3";
            }else if(pathDetail.equals(Constants.MODIFIER_VERY_STRONG)){
                markerValue += "4";
            }else if(pathDetail.equals(Constants.MODIFIER_STAND_ALONE)){
                markerValue += "5";
            }
        }

        return markerValue;
    }

    private void sortFailedRuleSet(Map<String, ArrayList<RuleConditionDTO>> failedRuleSetMap){
        Iterator<String> iter = failedRuleSetMap.keySet().iterator();
        mainLoop:
        while(iter.hasNext()){
            String key = iter.next();
            ArrayList<RuleConditionDTO> ruleConditions = failedRuleSetMap.get(key);
            if(ruleConditions.size() == 0){
                continue mainLoop;
            }

            while(true){
                boolean switchMade = false;
                int n = ruleConditions.size();
                for(int i=0; n>i; i++){
                    RuleConditionDTO rule = ruleConditions.get(i);
                    int currentCondLeft = rule.getConditionsLeft();
                    if(i < (n-1) && ruleConditions.get(i+1) != null && currentCondLeft > ruleConditions.get(i+1).getConditionsLeft()){
                        RuleConditionDTO tempRule = ruleConditions.get((i+1));
                        ruleConditions.set((i+1), rule);
                        ruleConditions.set(i, tempRule);
                        switchMade = true;
                    }
                }
                if(!switchMade){
                    break;
                }
            }

        }
    }

    private void add_GN001_Data(JSONArray dataArray){
        String jsonStr = "{\"@id\": \"https://cspec.genome.network/cspec/api/SequenceVariantInterpretation/id/GN001\",\n" +
                "\t\t\t\"affiliation\": {\n" +
                "\t\t\t\t\"@id\": \"https://cspec.genome.network/cspec/api/Organization/id/ACMG\",\n" +
                "\t\t\t\t\"@type\": \"Organization\",\n" +
                "\t\t\t\t\"label\": \"American College of Medical Genetics and Genomics\",\n" +
                "\t\t\t\t\"url\": \"https://www.acmg.net\"\n" +
                "\t\t\t},\n" +
                "\t\t\t\"ruleSets\": [\n" +
                "\t\t\t\t{\n" +
                "\t\t\t\t\t\"@id\": \"https://cspec.genome.network/cspec/api/RuleSet/id/135641113\",\n" +
                "\t\t\t\t\t\"@type\": \"RuleSet\",\n" +
                "\t\t\t\t\t\"genes\":[]\n" +
                "\t\t\t\t}\n" +
                "\t\t\t],\n" +
                "\t\t\t\"status\": \"Released\",\n" +
                "\t\t\t\"url\": \"https://cspec.genome.network/cspec/ui/svi/doc/GN001\",\n" +
                "\t\t\t\"version\": \"2.0.0\"\n" +
                "\t\t}";

        try {
            JSONObject gn001 = (JSONObject) jsonParser.parse(jsonStr);
            dataArray.add(gn001);
        }catch (Exception e){
            logger.error(StackTracePrinter.printStackTrace(e));
        }
    }

    @Data
    private class MainRulesAndCriteriaCodes{
        private JSONArray mainRules;
        private JSONArray criteriaCodes;

        public MainRulesAndCriteriaCodes(JSONArray mainRules, JSONArray criteriaCodes){
            this.mainRules = mainRules;
            this.criteriaCodes = criteriaCodes;
        }
    }
}

   /*
    private String getAPaginatedListOfVCEPs(String pageNumberParam, String pageSizeParam){
        HashMap<String,String> httpProperties = new HashMap<String,String>();
        httpProperties.put(Constants.CONTENT_TYPE, Constants.CONTENT_TYPE_APP_JSON);

        String listOfAllCSpecEnginesWithPgNumURL =  listOfAllCSpecEngines + pageNumberParam + pageSizeParam;;

        HTTPSConnector https = new HTTPSConnector();
        String response = https.sendHttpsRequest(listOfAllCSpecEnginesWithPgNumURL, Constants.HTTP_GET, null, httpProperties);
        return response;
    }*/

/*
    private ArrayList<JSONObject> getAListOfBasicVCEPDataOldAPI(){
        ArrayList<JSONObject> completeResponseList = null;
        String  enginesListResponse = null;
        int pageNum = 1;
        int iterLimit = 150;
        int totalNumOfEnginesFromResponse = 0;
        logger.info("Getting response from CSpecEngines API, "+numOfEnginesPerPage+" per page!");
        mainLoop:
        while(true){
            enginesListResponse = getAPaginatedListOfVCEPs("&pg=" + pageNum, "&pgSize="+numOfEnginesPerPage);
            if(enginesListResponse != null && !enginesListResponse.equals("")){
                JSONArray dataArray = null;
                try {
                    if(jsonParser == null){
                        jsonParser = new JSONParser();
                    }
                    JSONObject obj = (JSONObject) jsonParser.parse(enginesListResponse);
                    dataArray = (JSONArray) obj.get("data");
                    if(dataArray != null && dataArray.size() > 0){
                        if(dataArray.size() < numOfEnginesPerPage){
                            pageNum = 100; //first measure to make sure that the loop stops
                        }
                        if(completeResponseList == null){
                            completeResponseList = new ArrayList<JSONObject>();
                        }
                        for(Object dataObj : dataArray){
                            completeResponseList.add((JSONObject) dataObj);
                        }
                        totalNumOfEnginesFromResponse += dataArray.size();

                        if(dataArray.size() < numOfEnginesPerPage){
                            break mainLoop; //second measure to make sure that the loop stops
                        }
                    }
                }catch(Exception e){
                    logger.error(StackTracePrinter.printStackTrace(e));
                }
            }
            pageNum++;
            if(pageNum == iterLimit){
                //something is wrong at this point!
                logger.error("The engines loop has iterated "+iterLimit+" times!");
                break mainLoop;
            }
        }

        logger.info("Finished collecting Engines(RuleSets) from responses, gathered total num: "+totalNumOfEnginesFromResponse);
        return completeResponseList;
    }
* */
