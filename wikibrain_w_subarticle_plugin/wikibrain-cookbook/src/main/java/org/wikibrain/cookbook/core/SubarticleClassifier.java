package org.wikibrain.cookbook.core;

/**
 * Created by allenlin on 5/24/16.
 */

import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class SubarticleClassifier {
    public LocalPageDao lpDao;
    private LocalLinkDao llDao;
    private RawPageDao rpDao;
    public ArrayList<List<String>> pages;
    public Configurator conf;
    private UniversalPageDao conceptDao;
    private MediaWikiParser jwpl;
    private LocalPage lp_original_mainArticle;
    private Language original_Language;
    private SRMetric sr_en;

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

    private String ZhTradition2Simplified(String inputZh){
        ZHConverter converter = ZHConverter.getInstance(ZHConverter.SIMPLIFIED);

        if (converter == null){
            System.out.println("The ZHConverter does not exist!");
        }

        return converter.convert(inputZh);
    }

    private final class ResultPotentialArticle {
        private boolean potential;
        private boolean mainTemplate;
        private boolean seeAlsoSection;

        ResultPotentialArticle(boolean potential, boolean mainTemplate, boolean seeAlsoSection){
            this.potential = potential;
            this.mainTemplate = mainTemplate;
            this.seeAlsoSection = seeAlsoSection;
        }

        boolean getPotential(){
            return this.potential;
        }

        boolean getMainTemplate(){
            return this.mainTemplate;
        }

        boolean getSeeAlsoSection(){
            return this.seeAlsoSection;
        }

    }

    private String getCorrespondingMainSectionTitle(LocalPage lang_main, LocalPage lang_sub, MediaWikiParser jwpl) throws DaoException{
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

    private boolean containLangAgnostic(String first, String second, Language lang){
        String first_synced = first;
        String second_synced = second;

        if (lang.getLangCode().equals("zh")){
            first_synced = ZhTradition2Simplified(first);
            second_synced = ZhTradition2Simplified(second);
        }

        return  StringUtils.containsIgnoreCase(first_synced, second_synced);
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

    private List<String> getSubArticle(RawPage rp_main, Template t, SubarticleParser subarticleParser){

        ParsedLink.SubarticleType tempSubType;

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

    public SubarticleClassifier(CommandLine cmd){
        //initialize the WikiBrain environment
        try {
            // Initialize the WikiBrain environment and get the local page dao
            Env env = new EnvBuilder(cmd).build();
            conf = env.getConfigurator();
            lpDao = conf.get(LocalPageDao.class);
            llDao = conf.get(LocalLinkDao.class);
            conceptDao = conf.get(UniversalPageDao.class);
            rpDao = conf.get(RawPageDao.class);

            MediaWikiParserFactory pf = new MediaWikiParserFactory();
            pf.setCalculateSrcSpans(true);
            jwpl = pf.createParser();
            //DEBUG: tempt out
//            sr_en = conf.get(
//                    SRMetric.class, "milnewitten",
//                    "language", "en");

        } catch (ConfigurationException configEx){
            configEx.printStackTrace();
        }

    }

    private Double Compute_NumLangsRatio(LocalPage lp_subArticle) throws DaoException{
        Double result;

        LocalPage lp_mainArticle = lp_original_mainArticle;

        int main_NumLang;
        int sub_NumLang;

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
            result = (double) main_NumLang / (double) sub_NumLang ;
        }
        else {
            result = 50.00;
        }

        return result;
    }

    private Double Compute_PotSubLangsRatio(LocalPage lp_subArticle) throws DaoException{
        Double result;


        int numLangCoexist = 0;
        int numLangPot = 0;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        if (up_main == null || up_sub == null){
            numLangCoexist++;
            ResultPotentialArticle currentResult = decidePotentialSubarticle(lp_original_mainArticle, lp_subArticle, jwpl);

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
            result = (double) numLangPot/ (double) numLangCoexist;
        }
        else {
            result = -100.00;
        }

        return result;
    }

    private Double Compute_MaxTokenOverlap(LocalPage lp_subArticle) throws DaoException{
        Double result;

        double max_overlap = 0;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        if ( up_main == null || up_sub == null){
            List<String> main_token = tokenizeLanguageAgnostic(lp_original_mainArticle.getTitle().getCanonicalTitle(), lp_original_mainArticle.getLanguage());
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


        result = max_overlap;

        return result;

    }

    private Double Compute_SeeAlsoSectionPct(LocalPage lp_subArticle) throws DaoException{
        Double result;

        int numSeeAlso = 0;
        int numLangPot = 0;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        if (up_main == null || up_sub == null){
            ResultPotentialArticle currentResult = decidePotentialSubarticle(lp_original_mainArticle, lp_subArticle, jwpl);

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
            result = (double) numSeeAlso/ (double) numLangPot;
        }
        else {
            result = -100.00;
        }

        return result;
    }

    private Double Compute_MainTemplateRatio(LocalPage lp_subArticle) throws DaoException{
        Double result;

        int numLangMainTemplates = 0;
        int numLangPot = 0;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        //If either up_main and up_sub does not exist, at least
        if (up_main == null || up_sub == null){
            ResultPotentialArticle currentResult = decidePotentialSubarticle(lp_original_mainArticle, lp_subArticle, jwpl);
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
            result = (double) numLangMainTemplates/ (double) numLangPot;
        }
        else {
            result = -100.00;
        }

        return result;
    }

    private Double Compute_MaxSectionTokenOverlap(LocalPage lp_subArticle) throws DaoException{
        Double result;

        double max_overlap = 0;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        if (up_main == null || up_sub == null){
            String mainSectionTitle = getCorrespondingMainSectionTitle(lp_original_mainArticle, lp_subArticle, jwpl);

            List<String> mainSectionTokens = tokenizeLanguageAgnostic(mainSectionTitle, lp_original_mainArticle.getLanguage());
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

        result = max_overlap;

        return result;
    }

    private Double Compute_MaxMainTFInSub(LocalPage lp_subArticle) throws DaoException{
        Double result;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        double maxTF = 0;

        if ( up_main == null || up_sub == null){
            double curTF = 0;
            RawPage rp_sub = rpDao.getById(lp_subArticle.getLanguage(), lp_subArticle.getLocalId());

            ParsedPage pp_sub = jwpl.parse(rp_sub.getBody());

            String summary = pp_sub.getFirstParagraph().getText();
            String main_title = lp_original_mainArticle.getTitle().getCanonicalTitle();

            if (containLangAgnostic(summary, main_title, lp_original_mainArticle.getLanguage())){
                summary = summary.toLowerCase();
                main_title = main_title.toLowerCase();
                curTF += StringUtils.countMatches(summary, main_title);
            }

            List<String> summaryTokens = tokenizeLanguageAgnostic(summary,lp_original_mainArticle.getLanguage());
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

        result = maxTF;
        return result;

    }

    private Double Compute_InlinkRatio(LocalPage lp_subArticle) throws DaoException{
        Double result;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        if (up_main == null || up_sub == null){
            result = -100.00;
            return result;
        }

        int main_NumInlink = 0;
        int sub_NumInlink = 0;



        for (Language lang : up_main.getLanguageSet()) {
            LocalPage tp_page = lpDao.getById(lang, up_main.getLocalId(lang));
            if(tp_page != null){
                //TODO: original model is trained using original_language. Hopefully not a huge differences.
                DaoFilter dFilter = new DaoFilter()
                        .setLanguages(lang)
                        .setDestIds(tp_page.getLocalId());
                main_NumInlink += llDao.getCount(dFilter);
            }
        }

        for (Language lang : up_sub.getLanguageSet()) {
            LocalPage tp_page = lpDao.getById(lang, up_main.getLocalId(lang));
            if(tp_page != null){
                //TODO: original model is trained using original_language. Hopefully not a huge differences.
                DaoFilter dFilter = new DaoFilter()
                        .setLanguages(lang)
                        .setDestIds(tp_page.getLocalId());
                sub_NumInlink += llDao.getCount(dFilter);
            }
        }

        if (sub_NumInlink != 0 ){
            result = (double) main_NumInlink/ (double) sub_NumInlink;
        }
        else {
            result = 100.00;
        }

        return result;
    }

    //TODO: currently only implement English milnewitten
    private Double ComputeMilneWitten(LocalPage lp_subArticle) throws DaoException{

        Double result = 0.0;

        UniversalPage up_main = conceptDao.getByLocalPage(lp_original_mainArticle);
        UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

        if (up_main == null || up_sub == null){
            result = -100.00;
            return result;
        }

        SRResult similarity = sr_en.similarity(lp_original_mainArticle.getLocalId(), lp_subArticle.getLocalId(), false);
        result = Double.isNaN(similarity.getScore())? 0 : similarity.getScore();


        return result;
    }


    /**
     * Wrapper function to compute the values for each feature
     * @param lp_subArticle: The LocalPage for the potential subarticle
     * @return dl_FeaturesValues: An array of the values of the feature for machine learning
     * @throws DaoException
     */
    private List<Double> ComputeFeaturesValues(LocalPage lp_subArticle) throws DaoException{
        ArrayList<Double> dl_FeaturesValues = new ArrayList<Double>();

        //NumLangsRatio
        dl_FeaturesValues.add(Compute_NumLangsRatio(lp_subArticle));

        //PotSubLangsRatio
        dl_FeaturesValues.add(Compute_PotSubLangsRatio(lp_subArticle));

        //MaxTokenOverlap
        dl_FeaturesValues.add(Compute_MaxTokenOverlap(lp_subArticle));

        //SeeAlsoSectionPct
        dl_FeaturesValues.add(Compute_SeeAlsoSectionPct(lp_subArticle));

        //MainTemplateRatio
        dl_FeaturesValues.add(Compute_MainTemplateRatio(lp_subArticle));

        //MaxSectionTokenOverlap
        dl_FeaturesValues.add(Compute_MaxSectionTokenOverlap(lp_subArticle));

        //MaxMainTFInSub
        dl_FeaturesValues.add(Compute_MaxMainTFInSub(lp_subArticle));

        //InlinkRatio
        dl_FeaturesValues.add(Compute_InlinkRatio(lp_subArticle));

        //MilneWitten
        dl_FeaturesValues.add(ComputeMilneWitten(lp_subArticle));

        return dl_FeaturesValues;
    }

    private boolean PredictLabel(List<Double> featureValues, String dataset, String rating){
        HttpClient httpclient = HttpClients.createDefault();
        //TODO: hard coded URL
        HttpPost httppost = new HttpPost("http://127.0.0.1:5000/");
        boolean label = false;
        try {
            // Request parameters and other properties.
            List<NameValuePair> params = new ArrayList<NameValuePair>(2);
            params.add(new BasicNameValuePair("dataset", dataset));

            rating = rating.equals("2.5") ? "25": rating;
            params.add(new BasicNameValuePair("rating", rating));
            params.add(new BasicNameValuePair("data1",Arrays.toString(featureValues.toArray())));
            httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));

            //Execute and get the response.
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                InputStream instream = entity.getContent();
                StringWriter writer = new StringWriter();
                IOUtils.copy(instream, writer, "UTF-8");
                String theString = writer.toString();
                if (theString.contains("\"prediction\": \"1.0\"")){
                    label = true;
                }
                instream.close();
            }
        } catch (IOException ioExc){
            ioExc.printStackTrace();
        }

        return label;

    }

    public List<String> FindSubarticles(String str_mainTitle, String lang_code, String dataset, String rating){
        ArrayList<String> subarticles = new ArrayList<String>();

        try{
            List<String> potentialSubarticles = ParsePotentialSubarticle(str_mainTitle, lang_code);

            if (!potentialSubarticles.isEmpty()){
                for (String current_pot_article: potentialSubarticles){
                    LocalPage lp_subArticle = lpDao.getByTitle(original_Language, current_pot_article);
                    if (lp_subArticle == null){
                        System.out.println("subarticle "+ current_pot_article + " does not exist, throw it away!");
                    }
                    else{
                        List<Double> featureValues = ComputeFeaturesValues(lp_subArticle);

                        //DEBUG: only used for debugging
                        System.out.println("main article: "+str_mainTitle+" | sub article: "+current_pot_article + " |Feature vector: " + Arrays.toString(featureValues.toArray()));

                        if(PredictLabel(featureValues, dataset, rating)){
                            subarticles.add(current_pot_article);
                        }
                    }

                }
            }
        }
        catch (DaoException daoExc){
            daoExc.printStackTrace();
        }

        return subarticles;
    }




    private List<String> ParsePotentialSubarticle(String str_mainTitle, String lang_code) throws DaoException{
        ArrayList<String> potentialSubarticles = new ArrayList<String>();

        original_Language = Language.getByLangCode(lang_code);
        lp_original_mainArticle = lpDao.getByTitle(original_Language, str_mainTitle);

        if (lp_original_mainArticle != null){
            RawPage rp_main = rpDao.getById(original_Language, lp_original_mainArticle.getLocalId());

            ParsedPage pp_main = jwpl.parse(rp_main.getBody());

            LanguageInfo localLangInfo= LanguageInfo.getByLanguage(original_Language);
            SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

            //Add potential subarticles in the templates
            for (Template curTemplate: pp_main.getTemplates()){
                List<String> tp_subarticleSet = getSubArticle(rp_main, curTemplate, subarticleParser);
                if (tp_subarticleSet != null){
                    potentialSubarticles.addAll(tp_subarticleSet);
                }
            }

            //Add potential subarticles in the see also section
            for (Section curSection: pp_main.getSections()){
                ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                if (secSubType != null){
                    for (Link cur_link : curSection.getLinks()){
                        potentialSubarticles.add(cur_link.getText());
                    }
                }
            }
        }
        else{
            System.out.println("Can't find article for the title "+str_mainTitle+" in "+lang_code);
        }

        return potentialSubarticles;
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

        String title = args[0];
        String lang_code = args[1];
        String dataset = args[2];
        String rating = args[3];


        SubarticleClassifier subarticleClassifier = new SubarticleClassifier(cmd);

        subarticleClassifier.FindSubarticles(title, lang_code, dataset, rating);
    }
}
