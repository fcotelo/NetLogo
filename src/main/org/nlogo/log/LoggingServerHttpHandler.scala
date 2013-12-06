package org.nlogo.log

import scala.collection.mutable.HashMap
import java.net.URL


private object LoggingServerHttpHandler {

  private val LogPostKey = "logging_data"

  /*
  Maps a root destination URL (i.e. "http://www.awesomelogs.com/logging") to a URL that logging data
  can be POSTed to (i.e. "http://www.awesomelogs.com/logging/152" or even "http://www.logstash.gov")

  DOES NOT SUPPORT SIMULTANEOUSLY WRITING MULTIPLE LOGS TO THE SAME ROOT URL
   */
  private val rootToLogMap = new HashMap[String, URL]

  def sendMessage(message: String, url: URL): String = {
    val dest = rootToLogMap.getOrElse(url.toString, obtainSendingURL(url))
    val packet = ZipUtils.gzipAsString(message)
    NetUtils.httpPost(Map(LogPostKey -> packet), dest)._2
  }

  private def obtainSendingURL(url: URL): URL = {
    val sendingUrl = queryFor(url)
    rootToLogMap += url.toString -> sendingUrl
    sendingUrl
  }

  private def queryFor(url: URL): URL = {
    new URL(url.toString + NetUtils.httpGet(url)._1)
  }

}
