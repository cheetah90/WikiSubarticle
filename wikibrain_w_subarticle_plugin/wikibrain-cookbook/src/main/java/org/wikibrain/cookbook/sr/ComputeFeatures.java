package org.wikibrain.cookbook.sr;

import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;
import org.apache.commons.cli.*;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.*;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageInfo;
import org.wikibrain.core.model.*;
import org.wikibrain.core.nlp.StringTokenizer;
import org.wikibrain.core.nlp.ZHConverter;
import org.wikibrain.parser.wiki.ParsedLink;
import org.wikibrain.parser.wiki.SubarticleParser;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.SRResult;
import de.tudarmstadt.ukp.wikipedia.parser.*;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;

public class ComputeFeatures {
    public LocalPageDao lpDao;
    public LocalLinkDao llDao;
    public RawPageDao rpDao;
    public ArrayList<List<String>> pages;
    public Configurator conf;
    public UniversalPageDao conceptDao;

    private ArrayList<List<String>> readCsv2Array(String fileName){
        BufferedReader br = null;
        String line;
        String cvsSplitBy = "\t";

        ArrayList<List<String>> pagesPair = new ArrayList<List<String>>();

        try {

            br = new BufferedReader(new FileReader(fileName));

            //Skip the first header line
            br.readLine();
            while ((line = br.readLine()) != null) {

                // use comma as separator
                List<String> record = new ArrayList<String>(Arrays.asList(line.split(cvsSplitBy)));
                pagesPair.add(record);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return pagesPair;
    }

    public ComputeFeatures(String fileName, CommandLine cmd){
        //initialize the WikiBrain environment
        try {
            // Initialize the WikiBrain environment and get the local page dao
            Env env = new EnvBuilder(cmd).build();
            conf = env.getConfigurator();
            lpDao = conf.get(LocalPageDao.class);
            llDao = conf.get(LocalLinkDao.class);
            conceptDao = conf.get(UniversalPageDao.class);
            rpDao = conf.get(RawPageDao.class);

        } catch (ConfigurationException configEx){
            configEx.printStackTrace();
        }

        pages = readCsv2Array(fileName);
    }

    public void writeToFile(String fileName, ArrayList<Double> results){
        try {
            PrintWriter writer = new PrintWriter(fileName, "UTF-8");
            for (Double result : results){
                writer.println(result);
            }
            writer.close();
        } catch (UnsupportedEncodingException UEE){
            UEE.printStackTrace();
        } catch (FileNotFoundException FFE){
            FFE.printStackTrace();
        }

    }

    public ArrayList<Double> ComputeSR(String SR_algorithm){

        ArrayList<Double> SR_results = new ArrayList<Double>();

        try {
            SRMetric sr_en = conf.get(
                    SRMetric.class, SR_algorithm,
                    "language", "en");

            SRMetric sr_es = conf.get(
                    SRMetric.class, SR_algorithm,
                    "language", "es");

            SRMetric sr_zh = conf.get(
                    SRMetric.class, SR_algorithm,
                    "language", "zh");

            HashMap<String, SRMetric> dictLanguage2SRMetric = new HashMap<String, SRMetric>();

            dictLanguage2SRMetric.put("en", sr_en);
            dictLanguage2SRMetric.put("es", sr_es);
            dictLanguage2SRMetric.put("zh", sr_zh);


            for (List<String> pagePair : pages){
                String current_lang = pagePair.get(1);

                Language language = Language.getByLangCode(current_lang);

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle != null && lp_subArticle != null){
                    SRResult similarity = dictLanguage2SRMetric.get(current_lang).similarity(lp_mainArticle.getLocalId(), lp_subArticle.getLocalId(), false);
                    double score = Double.isNaN(similarity.getScore())? 0 : similarity.getScore();
                    SR_results.add(score);
                }
                else {
                    //Missing value = 0
                    SR_results.add(0.0);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
            System.out.println("finish computing " + SR_algorithm);
            return SR_results;

        } catch (ConfigurationException e){
            e.printStackTrace();
        } catch (DaoException e){
            e.printStackTrace();
        }

        return SR_results;
    }

//    public ArrayList<Double> Compute_PageRankRatio(){
//        ArrayList<Double> result = new ArrayList<Double>();
//
//        try {
//            for (List<String> pagePair : pages){
//
//                Language language = Language.getByLangCode(pagePair.get(1));
//
//                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
//                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));
//
//
//                if (lp_mainArticle != null && lp_subArticle != null){
//                    //double pageRankRatio = llDao.getPageRank(language, lp_mainArticle.getLocalId())/llDao.getPageRank(language,lp_subArticle.getLocalId());
//                    //result.add(pageRankRatio);
//                }
//                else {
//                    result.add(-100.00);
//                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
//                }
//            }
//        } catch (DaoException daoException){
//            daoException.printStackTrace();
//        }
//
//        return result;
//
//    }

    public ArrayList<Double> Compute_NumLangsRatio(){
        ArrayList<Double> result = new ArrayList<Double>();


        try {
            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));
                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int main_NumLang = 0;
                int sub_NumLang = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    if (up_main == null){
                        main_NumLang = 1;
                        if (up_sub == null){
                            sub_NumLang = 1;
                        } else{
                            sub_NumLang = up_sub.getLanguageSet().size();
                        }

                    }
                    else {
                        sub_NumLang = 1;
                        main_NumLang = up_main.getLanguageSet().size();
                    }
                }
                else {
                    main_NumLang = up_main.getLanguageSet().size();
                    sub_NumLang = up_sub.getLanguageSet().size();
                }

                if (sub_NumLang != 0 ){
                    double NumLangsRatio = (double) main_NumLang / (double) sub_NumLang ;
                    result.add(NumLangsRatio);
                }
                else {
                    result.add(Double.MAX_VALUE);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    public ArrayList<Double> Compute_PotSubLangsRatio(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();


        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));


                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numLangCoexist = 0;
                int numLangPot = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    numLangCoexist++;
                    ResultPotentialArticle currentResult = decidePotentialSubarticle(lp_mainArticle, lp_subArticle, jwpl);

                    if (currentResult.getPotential()){
                        numLangPot++;
                    }
                }
                else {
                    for (Language lang : up_main.getLanguageSet()) {
                        if (up_sub.getLanguageSet().containsLanguage(lang)){

                            numLangCoexist++;
                            LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                            LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                            ResultPotentialArticle currentResult = decidePotentialSubarticle(lang_main, lang_sub, jwpl);
                            if (currentResult.getPotential()){
                                numLangPot++;
                            }
                        }
                    }
                }

                if (numLangCoexist != 0 ){
                    double PotSubRatio = (double) numLangPot/ (double) numLangCoexist;
                    result.add(PotSubRatio);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": " + PotSubRatio);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;
    }

    /**
     *
     * @param rp_main: the rawpage of the main article
     * @param t:
     * @param subarticleParser
     * @return
     */
    private List<String> getSubArticle(RawPage rp_main, Template t, SubarticleParser subarticleParser){

        ParsedLink.SubarticleType tempSubType = null;

        /**
         * Hack from WikiTextParser.java
         */
        boolean errorWithSrcLocation = t.getSrcSpan().getEnd() < 0; // this checks for what seems to be when parsing fails in JWPL
        String templateTextOrig;
        if (!errorWithSrcLocation){
            templateTextOrig = rp_main.getBody().substring(t.getSrcSpan().getStart(), t.getSrcSpan().getEnd());
        }else{ // this makes up for errors in JWPL (or bad script, but it mostly looks like erros)
            int estimatedLength = t.getPos().getEnd() - t.getPos().getStart();
            templateTextOrig = rp_main.getBody().substring(t.getSrcSpan().getStart(), t.getSrcSpan().getStart() + estimatedLength + 1);
        }
        String templateText;
        if (templateTextOrig.length() >= 5){
            templateText = templateTextOrig.substring(2, templateTextOrig.length()-2);
        }else{
            return null;
        }

        String templateName = new Title(t.getName(), false, LanguageInfo.getByLanguage(rp_main.getLanguage())).getCanonicalTitle();
        tempSubType = subarticleParser.isTemplateSubarticle(templateName, templateText);

        if (tempSubType != null){
            return subarticleParser.getContentsOfTemplatePipe(templateText);
        }

        return null;

    }

    public ArrayList<Double> Compute_NumPotSubarticleRatio(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numMain = 0;
                int numSub = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                for (Language lang : up_main.getLanguageSet()) {
                    LanguageInfo localLangInfo= LanguageInfo.getByLanguage(lang);
                    SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

                    LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));

                    RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());

                    ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                    for (Section curSection: pp_main.getSections()){
                        ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                        if (secSubType != null){
                            numMain+=curSection.getNestedLists().size();
                        }

                        for (Content curContent : curSection.getContentList()){
                            for (Template t : curContent.getTemplates()){
                                List<String> tp_subarticleSet = getSubArticle(rp_main, t, subarticleParser);
                                if (tp_subarticleSet != null){
                                    numMain+= tp_subarticleSet.size();
                                }
                            }
                        }
                    }
                }

                for (Language lang : up_sub.getLanguageSet()) {
                    LanguageInfo localLangInfo= LanguageInfo.getByLanguage(lang);
                    SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

                    LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                    RawPage rp_main = rpDao.getById(lang, lang_sub.getLocalId());

                    ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                    for (Section curSection: pp_main.getSections()){
                        ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                        if (secSubType != null){
                            numSub+= curSection.getNestedLists().size();
                        }

                        for (Content curContent : curSection.getContentList()){
                            for (Template t : curContent.getTemplates()){
                                List<String> tp_subarticleSet = getSubArticle(rp_main, t, subarticleParser);
                                if (tp_subarticleSet != null){
                                    numSub+= tp_subarticleSet.size();
                                }
                            }
                        }
                    }
                }


                if (numSub != 0 ){
                    double PotSubRatio = (double) numMain/ (double) numSub;
                    result.add(PotSubRatio);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": " +PotSubRatio);
                }
                else {
                    result.add(Double.MAX_VALUE);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    public ArrayList<Double> Compute_SeeAlsoSectionPct(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numSeeAlso = 0;
                int numLangPot = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    ResultPotentialArticle currentResult = decidePotentialSubarticle(lp_mainArticle, lp_subArticle, jwpl);

                    if (currentResult.getPotential()){
                        numLangPot++;
                    }
                    if (currentResult.getSeeAlsoSection()){
                        numSeeAlso++;
                    }

                }
                else {
                    for (Language lang : up_main.getLanguageSet()) {
                        if (up_sub.getLanguageSet().containsLanguage(lang)){

                            LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                            LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                            ResultPotentialArticle currentResult = decidePotentialSubarticle(lang_main, lang_sub, jwpl);

                            if (currentResult.getPotential()){
                                numLangPot++;
                            }
                            if (currentResult.getSeeAlsoSection()){
                                numSeeAlso++;
                            }
                        }
                    }
                }

                if (numLangPot != 0 ){
                    double PotSubRatio = (double) numSeeAlso/ (double) numLangPot;
                    result.add(PotSubRatio);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": "+ PotSubRatio);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    private String ZhTradition2Simplified(String inputZh){
        ZHConverter converter = ZHConverter.getInstance(ZHConverter.SIMPLIFIED);

        if (converter == null){
            System.out.println("The ZHConverter does not exist!");
        }

        return converter.convert(inputZh);
    }

    public ArrayList<Double> Compute_ReferenceRatio(){


        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numRef_main = 0;
                int numRef_sub = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){

                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());
                        RawPage rp_sub = rpDao.getById(lang, lang_sub.getLocalId());
                        numRef_main += StringUtils.countMatches(rp_main.getBody(), "<ref>");
                        numRef_sub += StringUtils.countMatches(rp_sub.getBody(), "<ref>");

                    }
                }

                if (numRef_sub !=0){
                    double PotSubRatio = (double) numRef_main/ (double) numRef_sub;
                    result.add(PotSubRatio);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + PotSubRatio);
                }
                else {
                    result.add(Double.MAX_VALUE);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    // num(common token in sub)/num(token in main)
    private double countTokenOverlap(List<String> main_tokens, List<String> sub_tokens){
        int overlap = 0;
        for (String tk : main_tokens){
            if (sub_tokens.contains(tk)){
                overlap++;
            }
        }
        return (double) overlap/ (double) main_tokens.size();
    }

    public String getCorrespondingMainSectionTitle(LocalPage lang_main, LocalPage lang_sub, MediaWikiParser jwpl) throws DaoException{
        Language lang = lang_main.getLanguage();

        RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());
        ParsedPage pp_main = jwpl.parse(rp_main.getBody());

        for (Section curSection: pp_main.getSections()){
            for (Content curContent : curSection.getContentList()){
                if (containLangAgnostic(curContent.getTemplates().toString(), lang_sub.getTitle().getCanonicalTitle(), lang)){
                    return lang_main.getTitle().getCanonicalTitle() + curSection.getTitle();
                }
            }
        }

        return lang_main.getTitle().getCanonicalTitle();
    }

    public ArrayList<Double> Compute_MaxSectionTokenOverlap(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                double max_overlap = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    String mainSectionTitle = getCorrespondingMainSectionTitle(lp_mainArticle, lp_subArticle, jwpl);

                    List<String> mainSectionTokens = tokenizeLanguageAgnostic(mainSectionTitle, lp_mainArticle.getLanguage());
                    List<String> subSectionTokens = tokenizeLanguageAgnostic(lp_subArticle.getTitle().getCanonicalTitle(), lp_subArticle.getLanguage());

                    double curTokenOverlap = countTokenOverlap(mainSectionTokens, subSectionTokens);
                    if (curTokenOverlap > max_overlap){
                        max_overlap = curTokenOverlap;
                    }
                }
                else {
                    for (Language lang : up_main.getLanguageSet()) {
                        if (up_sub.getLanguageSet().containsLanguage(lang)){

                            LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                            LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                            String mainSectionTitle = getCorrespondingMainSectionTitle(lang_main, lang_sub, jwpl);

                            List<String> mainSectionTokens = tokenizeLanguageAgnostic(mainSectionTitle, lang_main.getLanguage());
                            List<String> subSectionTokens = tokenizeLanguageAgnostic(lang_sub.getTitle().getCanonicalTitle(), lang_sub.getLanguage());

                            double curTokenOverlap = countTokenOverlap(mainSectionTokens, subSectionTokens);
                            if (curTokenOverlap > max_overlap){
                                max_overlap = curTokenOverlap;
                            }
                        }
                    }
                }

                result.add(max_overlap);
                System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + max_overlap);
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;
    }

    private List<String> tokenizeLanguageAgnostic(String first, Language lang){
        String first_processed = first;
        List<String> tokens;

        if (lang.getLangCode().equals("zh")){
            first_processed = ZhTradition2Simplified(first);
        }
        if (lang.getLangCode().equals("zh") || lang.getLangCode().equals("ja")){
            //Handle zh and ja differently since they are not space separated
            tokens = new ArrayList<String>(Arrays.asList(first_processed.split("")));
            if(tokens.get(0).equals(""))
                tokens.remove(0);
        }
        else {
            StringTokenizer tokenizer = new StringTokenizer();
            tokens = tokenizer.getWords(lang,first_processed);
        }

        return tokens;
    }


    public ArrayList<Double> Compute_MaxTokenOverlap(){
        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                double max_overlap = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if ( up_main == null || up_sub == null){
                    List<String> main_token = tokenizeLanguageAgnostic(lp_mainArticle.getTitle().getCanonicalTitle(), lp_mainArticle.getLanguage());
                    List<String> sub_tokens = tokenizeLanguageAgnostic(lp_subArticle.getTitle().getCanonicalTitle(), lp_subArticle.getLanguage());

                    double curTokenOverlap = countTokenOverlap(main_token, sub_tokens);
                    if (curTokenOverlap > max_overlap){
                        max_overlap = curTokenOverlap;
                    }
                }
                else {
                    for (Language lang : up_main.getLanguageSet()) {
                        if (up_sub.getLanguageSet().containsLanguage(lang)){

                            LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                            LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                            List<String> main_tokens = tokenizeLanguageAgnostic(lang_main.getTitle().getCanonicalTitle(), lang_main.getLanguage());
                            List<String> sub_tokens = tokenizeLanguageAgnostic(lang_sub.getTitle().getCanonicalTitle(), lang_sub.getLanguage());

                            double curTokenOverlap = countTokenOverlap(main_tokens, sub_tokens);
                            if (curTokenOverlap > max_overlap){
                                max_overlap = curTokenOverlap;
                            }
                        }
                    }
                }




                result.add(max_overlap);
                System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + max_overlap);
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;

    }

    public ArrayList<Double> Compute_MaxMainTFInSub(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                double maxTF = 0;

                if ( up_main == null || up_sub == null){
                    double curTF = 0;
                    RawPage rp_sub = rpDao.getById(lp_subArticle.getLanguage(), lp_subArticle.getLocalId());

                    ParsedPage pp_sub = jwpl.parse(rp_sub.getBody());

                    String summary = pp_sub.getFirstParagraph().getText();
                    String main_title = lp_mainArticle.getTitle().getCanonicalTitle();

                    if (containLangAgnostic(summary, main_title, lp_mainArticle.getLanguage())){
                        summary = summary.toLowerCase();
                        main_title = main_title.toLowerCase();
                        curTF += StringUtils.countMatches(summary, main_title);
                    }

                    List<String> summaryTokens = tokenizeLanguageAgnostic(summary,lp_mainArticle.getLanguage());
                    curTF = curTF / summaryTokens.size();

                    maxTF = curTF > maxTF ? curTF : maxTF;
                }
                else {
                    for (Language lang : up_main.getLanguageSet()) {
                        if (up_sub.getLanguageSet().containsLanguage(lang)){
                            LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                            LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                            double curTF = 0;
                            RawPage rp_sub = rpDao.getById(lang, lang_sub.getLocalId());

                            ParsedPage pp_sub = jwpl.parse(rp_sub.getBody());

                            String summary;
                            if (pp_sub.getFirstParagraph() == null){
                                summary = "";
                            } else {
                                summary = pp_sub.getFirstParagraph().getText();
                            }


                            String main_title = lang_main.getTitle().getCanonicalTitle();

                            if (containLangAgnostic(summary, main_title, lang)){
                                summary = summary.toLowerCase();
                                main_title = main_title.toLowerCase();
                                curTF += StringUtils.countMatches(summary, main_title);
                            }

                            List<String> summaryTokens = tokenizeLanguageAgnostic(summary,lang);
                            curTF = curTF / summaryTokens.size();

                            maxTF = curTF > maxTF ? curTF : maxTF;
                        }
                    }
                }

                result.add(maxTF);
                System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + maxTF);


            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;

    }

    public ArrayList<Double> Compute_InlinkRatio(){
        ArrayList<Double> result = new ArrayList<Double>();

        try {
            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));
                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }


                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int main_NumInlink = 0;
                int sub_NumInlink = 0;



                for (Language lang : up_main.getLanguageSet()) {
                    LocalPage tp_page = lpDao.getById(lang, up_main.getLocalId(lang));
                    if(tp_page != null){
                        DaoFilter dFilter = new DaoFilter()
                                .setLanguages(lang)
                                .setDestIds(tp_page.getLocalId());
                        main_NumInlink += llDao.getCount(dFilter);
                        System.out.println("Main page: " + tp_page.getTitle() + "Inlink: " + main_NumInlink);
                    } else {
                        System.out.println("The local page does not exist!");
                    }
                }

                for (Language lang : up_sub.getLanguageSet()) {
                    LocalPage tp_page = lpDao.getById(lang, up_main.getLocalId(lang));
                    if(tp_page != null){
                        DaoFilter dFilter = new DaoFilter()
                                .setLanguages(language)
                                .setDestIds(tp_page.getLocalId());
                        sub_NumInlink += llDao.getCount(dFilter);
                        System.out.println("Sub page: " + tp_page.getTitle() + "Inlink: " + sub_NumInlink);
                    } else {
                        System.out.println("The local page is empty");
                    }
                }

                if (sub_NumInlink != 0 ){
                    double NumLangRatio = (double) main_NumInlink/ (double) sub_NumInlink;
                    result.add(NumLangRatio);
                }
                else {
                    result.add(100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }


            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    private boolean containLangAgnostic(String first, String second, Language lang){
        String first_synced = first;
        String second_synced = second;

        if (lang.getLangCode().equals("zh")){
            first_synced = ZhTradition2Simplified(first);
            second_synced = ZhTradition2Simplified(second);
        }

        return  StringUtils.containsIgnoreCase(first_synced, second_synced);
    }

    private boolean equalLangAgnostic(String first, String second, Language lang){
        String first_synced = first;
        String second_synced = second;

        if (lang.getLangCode().equals("zh")){
            first_synced = ZhTradition2Simplified(first);
            second_synced = ZhTradition2Simplified(second);
        }

        return  StringUtils.equalsIgnoreCase(first_synced, second_synced);
    }

    private ResultPotentialArticle decidePotentialSubarticle(LocalPage lang_main, LocalPage lang_sub, MediaWikiParser jwpl) throws DaoException{
        boolean potential = false;
        boolean mainTemplate = false;
        boolean seeAlsoSection = false;

        Language currentLang = lang_main.getLanguage();

        LanguageInfo localLangInfo= LanguageInfo.getByLanguage(currentLang);
        SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

        RawPage rp_main = rpDao.getById(currentLang, lang_main.getLocalId());
        ParsedPage pp_main = jwpl.parse(rp_main.getBody());


        //This checks if the sub title is in the main template. If yes, this pair has both main template and potential relationships
        for (Template template: pp_main.getTemplates()){

            String templateName = new Title(template.getName(), false, LanguageInfo.getByLanguage(currentLang)).getCanonicalTitle();
            ParsedLink.SubarticleType subarticleType = subarticleParser.isTemplateSubarticle(templateName, template.toString());
            //If this template is a subarticle template and contains the potential subarticle title, change the flag
            if (subarticleType != null && containLangAgnostic(template.toString(),lang_sub.getTitle().getCanonicalTitle(), lang_main.getLanguage())){
                potential = true;
                //additionally, if it's main template, change the flag
                if (subarticleType.equals(ParsedLink.SubarticleType.MAIN_TEMPLATE) ){
                    mainTemplate = true;
                    //No need to go further since we've already decided that this pair is potential subarticle.
                    break;
                }
            }
        }

        //if the pair is not template subarticle, then check if it is a see also section subarticle.
        if (!potential){
            for (Section curSection: pp_main.getSections()){
                ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                if (secSubType != null){
                    if (containLangAgnostic(curSection.getText(),lang_sub.getTitle().getCanonicalTitle(), lang_main.getLanguage())){
                        potential = true;
                        seeAlsoSection = true;

                    }
                }
            }
        }

        return new ResultPotentialArticle(potential, mainTemplate, seeAlsoSection);
    }

    public final class ResultPotentialArticle {
        private boolean potential;
        private boolean mainTemplate;
        private boolean seeAlsoSection;

        public ResultPotentialArticle(boolean potential, boolean mainTemplate, boolean seeAlsoSection){
            this.potential = potential;
            this.mainTemplate = mainTemplate;
            this.seeAlsoSection = seeAlsoSection;
        }

        public boolean getPotential(){
            return this.potential;
        }

        public boolean getMainTemplate(){
            return this.mainTemplate;
        }

        public boolean getSeeAlsoSection(){
            return this.seeAlsoSection;
        }

    }

    public ArrayList<Double> Compute_MainTemplatePct(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numLangMainTemplates = 0;
                int numLangPot = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                //If either up_main and up_sub does not exist, at least
                if (up_main == null || up_sub == null){
                    ResultPotentialArticle currentResult = decidePotentialSubarticle(lp_mainArticle, lp_subArticle, jwpl);
                    if (currentResult.getPotential()){
                        numLangPot++;
                    }
                    if (currentResult.getMainTemplate()){
                        numLangMainTemplates++;
                    }
                }
                else{
                    for (Language lang : up_main.getLanguageSet()) {
                        if (up_sub.getLanguageSet().containsLanguage(lang)){


                            LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                            LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                            ResultPotentialArticle currentResult = decidePotentialSubarticle(lang_main, lang_sub, jwpl);

                            if (currentResult.getPotential()){
                                numLangPot++;
                            }
                            if (currentResult.getMainTemplate()){
                                numLangMainTemplates++;
                            }
                        }
                    }
                }


                if (numLangPot != 0 ){
                    double mainTemplatePct = (double) numLangMainTemplates/ (double) numLangPot;
                    result.add(mainTemplatePct);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": "+ mainTemplatePct);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }


    public static void main(String[] args){
        Options options = new Options();
        EnvBuilder.addStandardOptions(options);

        CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("SRBuilder", options);
            return;
        }


        ComputeFeatures tp_computeFeature = new ComputeFeatures("trainingdata_completely_random.tsv", cmd);

//        ArrayList<Double> InlinksRatio = tp_computeFeature.Compute_InlinkRatio();
//        tp_computeFeature.writeToFile("InlinksRatio.csv", InlinksRatio);

//        ArrayList<Double> MaxMainTFInSub = tp_computeFeature.Compute_MaxMainTFInSub();
//        tp_computeFeature.writeToFile("MaxMainTFInSub.csv", MaxMainTFInSub);

        ArrayList<Double> mainTemplatePct = tp_computeFeature.Compute_MainTemplatePct();
        tp_computeFeature.writeToFile("mainTemplatePct.csv", mainTemplatePct);

//        ArrayList<Double> MaxTokenOverlap = tp_computeFeature.Compute_MaxTokenOverlap();
//        tp_computeFeature.writeToFile("MaxTokenOverlap.csv", MaxTokenOverlap);

        ArrayList<Double> MaxSectionTokenOverlap = tp_computeFeature.Compute_MaxSectionTokenOverlap();
        tp_computeFeature.writeToFile("MaxSectionTokenOverlap.csv", MaxSectionTokenOverlap);

//        ArrayList<Double> ReferenceRatio = tp_computeFeature.Compute_ReferenceRatio();
//               tp_computeFeature.writeToFile("ReferenceRatio.csv", ReferenceRatio);

//        ArrayList<Double> seeAlsoSectionPct = tp_computeFeature.Compute_SeeAlsoSectionPct();
//        tp_computeFeature.writeToFile("seeAlsoSectionPct.csv", seeAlsoSectionPct);

//        ArrayList<Double> NumPotSubarticleRatio = tp_computeFeature.Compute_NumPotSubarticleRatio();
//        tp_computeFeature.writeToFile("NumPotSubarticleRatio.csv", NumPotSubarticleRatio);

//        ArrayList<Double> PotSubLangsRatio = tp_computeFeature.Compute_PotSubLangsRatio();
//        tp_computeFeature.writeToFile("PotSubLangsRatio.csv", PotSubLangsRatio);

        ArrayList<Double> NumLangRatio = tp_computeFeature.Compute_NumLangsRatio();
        tp_computeFeature.writeToFile("NumLangRatio.csv", NumLangRatio);

        //ArrayList<Double> PageRankRatio = tp_computeFeature.Compute_PageRankRatio();
        //tp_computeFeature.writeToFile("pageRank.csv", PageRankRatio);

//        ArrayList<Double> milnewitten = tp_computeFeature.ComputeSR("milnewitten", "en");
//        ArrayList<Double> category = tp_computeFeature.ComputeSR("category");
//
//        tp_computeFeature.writeToFile("milnewitten.csv", milnewitten);
//        //tp_computeFeature.writeToFile("category.csv", category);

    }

}
