package com.datajhoom.sentiment

import org.apache.spark.streaming.{Seconds, StreamingContext}

import org.apache.spark.SparkContext._
import org.apache.spark.streaming.twitter._
import org.apache.spark.SparkConf
import scala.collection.immutable.TreeMap
import scala.util.Random;
import net.liftweb.json._
import java.io.File;
import java.io.FileWriter;


/**
 * The twitter stream is instantiated with credentials and company specific filters are supplied by the command line arguments.
 */

object TwitterStream {
 
  // Uses Yahoo YQL to get stock quote
  // Parse JSON to get the ask price
  def getStockPrice(company : String) : Double = {
	val url="http://query.yahooapis.com/v1/public/yql?format=json&q=select%20Symbol,Ask,Volume,LastTradeTime%20from%20yahoo.finance.quotes%20where%20symbol%20in%20(%22"+company+"%22)&env=store://datatables.org/alltableswithkeys"
	val result = scala.io.Source.fromURL(url).mkString
	//println(result)

	val json = parse(result)
	val JString(price)  = ((((json \ "query") \ "results") \ "quote") \ "Ask")
	val ret = price.toDouble
	ret
  }

  def main(args: Array[String]) {
    if (args.length < 4) {
      System.err.println("Usage: TwitterPopularTags <consumer key> <consumer secret> " +
        "<access token> <access token secret> [<filters>]")
      System.exit(1)
    }

    val Array(consumerKey, consumerSecret, accessToken, accessTokenSecret) = args.take(4)

    // Set the system properties so that Twitter4j library used by twitter stream
    // can use them to generat OAuth credentials
    System.setProperty("twitter4j.oauth.consumerKey", consumerKey)
    System.setProperty("twitter4j.oauth.consumerSecret", consumerSecret)
    System.setProperty("twitter4j.oauth.accessToken", accessToken)
    System.setProperty("twitter4j.oauth.accessTokenSecret", accessTokenSecret)

    val sparkConf = new SparkConf().setAppName("TwitterPopularTags")
    val ssc = new StreamingContext(sparkConf, Seconds(5))

    //eliminate stop words as noise
    val suppressList = List("a", "the", "he", "she", "they", "to", "in", "with", "my", "me", "you", "u", "i", "about", "an", "or", "as", "at", "be", "by", "for", "from", "is", "it", "of", "this", "that", "was")

    val filters = args.takeRight(args.length - 6)
    filters.foreach(println)
    val stream = TwitterUtils.createStream(ssc, None, filters)

    //create a list to accumulate words and stuff a dummy one to avoid the 0 index because mllib expects 1 based indices
    var wordList = List[String]()
    wordList = "2junk1" :: wordList
    val company = args(6)

        var company_price1 = 0.0
        var company_price2 = 0.0
        val words = stream.flatMap(status => { val company_txt = status.getText; 
					     if (company_txt.toLowerCase.contains(company.toLowerCase)) 
							company_txt.split(" ").filterNot(suppressList.contains(_)) 
					     else 
						        ""
					    })

        // accumulate words every 15 min, check delta in stock price +ve=>Buy 1 0=>Neutral -ve=>Sell
        // this is put into a file to be used to model the data
        val topCounts = words.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(args(5).toLong))
                     .map{case (topic, count) => (count, topic)}
                     .transform(_.sortByKey(false))

        // this is the data on which we apply the model to predict/recommend
        val predictCounts = words.map((_, 1)).reduceByKeyAndWindow(_ + _, Seconds(args(5).toLong))
                     .map{case (topic, count) => (count, topic)}
                     .transform(_.sortByKey(false))

	// data to generate the model
        //spit out top n words for each company
        topCounts.foreachRDD(rdd => {
          val topList = rdd.take(args(4).toInt)
	  company_price2 = Random.nextInt(100)
	  //val company_price2 = getStockPrice(company)
	  if ( company_price1 != 0 && topList.size > 0 ) {
          println("\nPopular topics in last x min (%s total ):".format(rdd.count()))
	  var tm = TreeMap[Int, String]()
          topList.foreach{case (count, tag) => { if(!wordList.contains(tag)) wordList=wordList :+ tag.toString; val ind=wordList.indexOf(tag); val tmp=ind+":"+count; tm += (ind -> tmp) }}
          tm.foreach{case (k, v) =>  print(" "+v) }
	  println("")
          val fw = new FileWriter(company+".data", true) ; 
	  if (company_price2-company_price1 >0 )
          fw.write("1")
	  else
	  fw.write("0")
          tm.foreach{case (k, v) =>  fw.write(" "+v) }
          fw.write("\n")
	  fw.close()
          }
	  company_price1 = company_price2
       
        })
    
        predictCounts.foreachRDD(rdd => {
          val topList = rdd.take(args(4).toInt)
          var tm = TreeMap[Int, String]()
          topList.foreach{case (count, tag) => { if(!wordList.contains(tag)) wordList=wordList :+ tag.toString; val ind=wordList.indexOf(tag); val tmp=ind+":"+count; tm += (ind -> tmp) }}
          tm.foreach{case (k, v) =>  print(" "+v) }
          println("")
          val fw = new FileWriter(company+"_last.data", false) ;
          fw.write(" "+company_price2)
          tm.foreach{case (k, v) =>  fw.write(" "+v) }
          fw.write("\n")
          fw.close()
        })

    ssc.start()
    ssc.awaitTermination()
  }
}
