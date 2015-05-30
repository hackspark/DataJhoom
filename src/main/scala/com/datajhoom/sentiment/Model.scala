package com.datajhoom.sentiment

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.classification.{LogisticRegressionWithLBFGS, LogisticRegressionModel}
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.util.MLUtils

/*
 * Model takes data collected in regular interval from Twitter Stream and computes the model
 * Input : MLLib expecta data in the form of <label> List<features> where each feature is <index:value>
 * Output : model 
 */
object Model {
    def main(args: Array[String]) {
    	val sparkConf = new SparkConf().setAppName("Model")
	val sc = new SparkContext(sparkConf)

	// Load training data in LIBSVM format.
	val data = MLUtils.loadLibSVMFile(sc, args(0)+".data")
	val modelPath = args(0)+"_ModelPath"

	// Split data into training (60%) and test (40%).
	val splits = data.randomSplit(Array(0.6, 0.4), seed = 11L)
	val training = splits(0).cache()
	val test = splits(1)

	// Run training algorithm to build the model
	val model = new LogisticRegressionWithLBFGS().setNumClasses(10).run(training)

	// Compute raw scores on the test set.
	val predictionAndLabels = test.map { case LabeledPoint(label, features) =>
  		val prediction = model.predict(features)
  		(prediction, label)
	}

	// Get evaluation metrics.
	val metrics = new MulticlassMetrics(predictionAndLabels)
	val precision = metrics.precision
	println("Precision = " + precision)

	// Save and load model
	model.save(sc, modelPath)
	val sameModel = LogisticRegressionModel.load(sc, modelPath)

	sc.stop
    }
}
