from flask import Flask, request, redirect, jsonify
from sklearn.externals import joblib
import numpy as np


app = Flask(__name__)
app.clfs = {"popular_25": joblib.load('./models/popular_25.pkl'), "popular_3": joblib.load('./models/popular_3.pkl'), "popular_2": joblib.load('./models/popular_2.pkl')}

@app.route("/", methods = ["GET", "POST"])


def hello():
    # If nothing is posted on the form just return home.html
    if request.method == "GET":
        #redirect to info page - now just redirect to GroupLens
        return redirect("http://grouplens.org/")
    else:
        # Otherwise go fetch the data and format it properly
        data_points = request.form
        dataset = request.form["dataset"]
        rating = request.form["rating"]
        data_array = []
        for iterator in data_points.items():
            if iterator[0] != "dataset" and iterator[0] != "rating":
                data = iterator[1][1:-1].split(",")
                data = [float(i) for i in data]
                data_array.append(np.array(data))

        # Load the model
        clf = app.clfs[dataset + "_" + rating]
        results = clf.predict(np.array(data_array))
        format_results = ','.join(map(str, results))
        return jsonify(prediction = format_results)

if __name__ == "__main__":
    app.run()