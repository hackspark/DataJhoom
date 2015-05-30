package com.datajhoom.sentiment

import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import java.io.File;
import java.io.FileWriter;

import org.apache.spark.mllib.classification.{LogisticRegressionWithLBFGS, LogisticRegressionModel}
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.util.MLUtils

import java.nio.file.{Paths, Files}

/**
 * Predict applies the model on the latest feature set collected for the model
 * and posts the result to rest web svc to be displayed on UI
 */

object Predict{

  def main(args: Array[String]) {

    val sparkConf = new SparkConf().setAppName("Predictor")
    val sc = new SparkContext(sparkConf)

    val company = args(0)
    val modelBasePath = args(1)
    val postUrlIP = args(2)
 
     val fullPath=modelBasePath+company+"_ModelPath"
     println(fullPath)
     if (Files.exists(Paths.get(fullPath))) {

          val model = LogisticRegressionModel.load(sc, fullPath)
          val data = MLUtils.loadLibSVMFile(sc, company+"_last.data") 
          val predictionAndLabels = data.map { case LabeledPoint(label, features) =>
  		val prediction = model.predict(features)
  		prediction
	   }

           val prediction = model.predict(data.collect()(0).features)
           println("Prediction="+prediction)
           var rec = "HOLD"
           if (prediction == 1.0)
		rec = "BUY"
           if (prediction == 0.0)
		rec = "SELL"

           val url = "http://"+postUrlIP+"/update?data="+company+"-"+data.collect()(0).label+"-"+rec
           println("PostData=" + url)
	    val result = scala.io.Source.fromURL(url).mkString
            println(result)

      }
  }
}

