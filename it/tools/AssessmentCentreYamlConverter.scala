/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools

import java.io.{File, PrintWriter}

import scala.collection.immutable.ListMap
import scala.io.Source
import scala.util.{Failure, Success, Try}

// Run in IntelliJ
//scalastyle:off printlnRegex
object AssessmentCentreYamlConverter extends App {
  val AssessmentsCSV = "/resources/assessment-centres.csv"
  val YamlSuffix = ".yaml"

  lazy val pathToCsv = (getClass getResource AssessmentsCSV).getPath
  lazy val pathToYamlDev = pathToCsv.dropRight(4) + YamlSuffix
  lazy val pathToYamlProd = pathToCsv.dropRight(4) + "-prod" + YamlSuffix

  lazy val lines = Source.fromURL(getClass getResource AssessmentsCSV).getLines().toList

  lazy val linesWithoutHeadersByRegion = lines.tail.map(_.split(",").toList).groupBy(_.head)

  def sortVenuesInRegionByVenueName(venuesInRegion: Map[String, List[AssessmentVenue]]) = {
    ListMap(venuesInRegion.toSeq.sortBy(_._1):_*)
  }

  def locationDetailsListToTuple(locationDetails: List[List[String]]) = {
    locationDetails.map {
      case _ :: venueDescription :: venueName :: capacityInfo => AssessmentVenue(venueName, venueDescription, capacityInfo)
      case _ => throw new IllegalArgumentException("There is a malformed line in the input.")
    }.groupBy(_.venueName)
  }

  lazy val parsedYaml = linesWithoutHeadersByRegion
    .map {
      case (region, locationDetails) =>
        val venuesInRegion = locationDetailsListToTuple(locationDetails)
        val sortedVenuesInRegion = sortVenuesInRegionByVenueName(venuesInRegion)

        (region, sortedVenuesInRegion)
    }

  lazy val stringToWrite = parsedYaml.flatMap { case (loc, venues) =>

    // Filter out venues with incomplete capacity/minattendee info
    val filterVenuesWithSlots: List[AssessmentVenue] => List[AssessmentVenue] = _.filter {
      item => item.capacityInfo.length == 7 && item.capacityInfo.forall(_.nonEmpty)
    }

    val venuesStrList = venues.collect { case (venueDescription, venueDetails) if filterVenuesWithSlots(venueDetails).nonEmpty  =>

      val capacities = "" +
          "      capacities:\n" + filterVenuesWithSlots(venueDetails).map { venueInfo =>

          s"""         - date: ${venueInfo.capacityInfo.head}
            |           amCapacity: ${venueInfo.capacityInfo(1)}
            |           amMinViableAttendees: ${venueInfo.capacityInfo(3)}
            |           amPreferredAttendeeMargin: ${venueInfo.capacityInfo(4)}
            |           pmCapacity: ${venueInfo.capacityInfo(2)}
            |           pmMinViableAttendees: ${venueInfo.capacityInfo(5)}
            |           pmPreferredAttendeeMargin: ${venueInfo.capacityInfo(6)}""".stripMargin
        }.mkString("\n")

        s"""  - ${venueDetails.head.venueName}:
           |      description: ${venueDetails.head.venueDescription}
           |$capacities""".stripMargin
    }

    venuesStrList.nonEmpty match {
      case true =>
        val venuesStr = venuesStrList.mkString("\n")
        Some(
            s"""$loc:
               |$venuesStr
         """.stripMargin
        )
      case false => None
    }
  }

  println("#################################################")
  println("#### Assessment Centre CSV to YAML Converter ####")
  println("#################################################")
  println("- Converting CSV to YAML: " + pathToCsv)
  writeFile(pathToYamlDev)
  writeFile(pathToYamlProd)
  println("- Done.")
  println()
  println("IMPORTANT: Remember to add one year to all dates in the dev environment configuration in order to pass Acceptance Tests")

  def writeFile(pathToOutputFile: String) = {
    val assessmentYamlFile = new File(pathToOutputFile)
    val result = Try {
      val writer = new PrintWriter(new File(pathToOutputFile))
      writer.write(stringToWrite.mkString("\n"))
      writer.close()
    }

    result match {
      case Success(_) =>
        println("  - YAML file written: " + assessmentYamlFile.getAbsolutePath)
      case Failure(ex) =>
        println("  - Error writing YAML file: ")
        ex.printStackTrace()
    }
  }
}
//scalastyle:on printlnRegex
