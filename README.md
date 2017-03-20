# WikiSubarticle
This is the repo for CSCW2017 paper *Lin, Y., Yu, B., Hall, A., and Hecht, B. (2017) Problematizing and Addressing the Article-as-Concept Assumption in Wikipedia. Proceedings of the 20th ACM Conference on Computer-Supported Cooperative Work and Social Computing (CSCW 2017). New York: ACM Press.*

The repo contains three parts:
1. The Java program **WikiSubarticle** in *./wikibrain_w_subarticle_plugin/*
2. The Python Flask program that serves the **trained Subarticle classifiers** in *./flask_classifiers/*
3. The **groud truth ratings** of subarticle candidates in *./gold_standard_datasets/* that allow for training your own subarticle classifiers

## Requirements
Java >= 1.7  
Maven >= 2  
Postgres >= 8.6  
Python >= 3.5  
Flask >= 0.12  

# Instructions
## Step 1 - Set up WikiSubarticle
The Java program **WikiSubarticle** leverages [WikiBrain](https://shilad.github.io/wikibrain/) to provide technical infrastucture to access Wikipedia content. Please follow the instructions on [WikiBrain](https://shilad.github.io/wikibrain/) to set up this part. 

**Note:** Currently, **WikiSubarticle** requires the training the MilneWitten Semantic Relatedness module of WikiBrain. Please refer to this page for [details of how to train the module](https://shilad.github.io/wikibrain/tutorial/sr.html)

## Step 2 - Set up Python Flask
From *./flask_classifiers/*, run `python classifiers_server.py`. Doing so will serve the trained subarticle classifiers through Flask so you don't need to train your own model

## Step 3 - Run the Subarticle Classifier

`wb-java.sh org.wikibrain.cookbook.core.SubarticleClassifier [main article title] [lang_code] [type of dataset] [rating options] -c [configuration]`

**Specifications of the parameters:**  
[main article title]: the program will find the subarticles of this Wikipedia article  
[lang_code]: three options "en" "es" "zh"  
[type of dataset]: one option "popular" (currently)
[rating options]: two options "2" "2.5" "3"

The meanings for each parameter could be seen in the paper. 
