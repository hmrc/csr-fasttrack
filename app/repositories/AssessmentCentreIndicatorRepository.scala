/*
 * Copyright 2017 HM Revenue & Customs
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

package repositories

import model.AssessmentCentreIndicator
import play.api.Play
import resource._

import scala.io.Source

object AssessmentCentreIndicatorCSVRepository extends AssessmentCentreIndicatorRepository {
  private val CsvFileName = "AssessmentCentreIndicatorLookup.csv"
  override val expectedNumberOfHeaders = 3

  import play.api.Play.current

  override private[repositories] val assessmentCentreIndicators: Map[String, AssessmentCentreIndicator] = {

    val input = managed(Play.application.resourceAsStream(CsvFileName).get)
    input.acquireAndGet { inputStream =>
      val rawData = Source.fromInputStream(inputStream).getLines.map(parseLine).toList
      val headers = rawData.head
      val values = rawData.tail

      def toMap(m: Map[String, AssessmentCentreIndicator], line: Array[String]): Map[String, AssessmentCentreIndicator] = {
        require(
          headers.length == line.length,
          s"Number of columns must be equal to number of headers. Incorrect line: ${line.mkString("|")}"
        )
        m + ((line(0), AssessmentCentreIndicator(line(1), line(2))))
      }

      values.foldLeft(Map.empty[String, AssessmentCentreIndicator])((acc, line) => toMap(acc, line))
    }
  }

  override def calculateIndicator(postcode: Option[String]): AssessmentCentreIndicator = postcode match {
    case None => DefaultIndicator
    case aPostcode => getIndicator(aPostcode)
  }

  private def getIndicator(postcode: Option[String]): AssessmentCentreIndicator = {
    postcode.map { pc =>
      val key = pc.takeWhile(!_.isDigit).toUpperCase
      assessmentCentreIndicators.get(key).fold[AssessmentCentreIndicator](DefaultIndicator)(indicator => indicator)
    } getOrElse DefaultIndicator
  }

}

trait AssessmentCentreIndicatorRepository extends CsvHelper {
  val DefaultIndicator = AssessmentCentreIndicator("London", "London")
  private[repositories] val assessmentCentreIndicators: Map[String, AssessmentCentreIndicator]
  def calculateIndicator(postcode: Option[String]): AssessmentCentreIndicator
}
