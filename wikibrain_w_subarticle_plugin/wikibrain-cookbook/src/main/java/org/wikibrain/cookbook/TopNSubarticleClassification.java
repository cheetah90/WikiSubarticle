package org.wikibrain.cookbook;

import org.apache.commons.cli.*;
import org.jooq.util.derby.sys.Sys;
import org.wikibrain.cookbook.core.SubarticleClassifier;
import org.wikibrain.cookbook.sr.ComputeFeatures;
import org.wikibrain.core.cmd.EnvBuilder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by allenlin on 5/25/16.
 */
public class TopNSubarticleClassification {
    private ArrayList<List<String>> pages;


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

    public TopNSubarticleClassification(String filename, CommandLine cmd){
        SubarticleClassifier subarticleClassifier = new SubarticleClassifier(cmd);

        pages = readCsv2Array(filename);

        for (List<String> pagePair: pages){
            List<String> trueSubartciles = subarticleClassifier.FindSubarticles(pagePair.get(1), pagePair.get(0), "popular", "2.5");
            if (!trueSubartciles.isEmpty()){
                System.out.println("Article: " + pagePair.get(1) + " has number of subarticle: " + trueSubartciles.size());
            }
        }
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

        String fileName = args[0];
        new TopNSubarticleClassification(fileName, cmd);
    }
}
