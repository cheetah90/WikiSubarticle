# WikiSubarticle
This is the repo for CSCW2017 paper *Lin, Y., Yu, B., Hall, A., and Hecht, B. (2017) Problematizing and Addressing the Article-as-Concept Assumption in Wikipedia. Proceedings of the 20th ACM Conference on Computer-Supported Cooperative Work and Social Computing (CSCW 2017). New York: ACM Press.*

It contains three parts:
1. The Java program **WikiSubarticle** in *./wikibrain_w_subarticle_plugin/*
2. The Python Flask program that serves the **trained Subarticle classifiers** in *./flask_classifiers/*
3. The **groud truth ratings** of subarticle candidates in *./gold_standard_datasets/* that allow for training your own subarticle classifiers

## Requirements
Java >= 1.7

Maven >= 2

Postgres >= 8.6

Python >= 3.5

Flask >= 0.12

## Instructions
### Step 1 - Set up WikiSubarticle
The Java program **WikiSubarticle** leverages [WikiBrain](https://shilad.github.io/wikibrain/) to provide technical infrastucture to access Wikipedia content. Please follow the instructions on [WikiBrain](https://shilad.github.io/wikibrain/) to set up this part. 

### Step 2 - Set up Python Flask
