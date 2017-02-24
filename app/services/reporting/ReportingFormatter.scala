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

package services.reporting

import model.Adjustments

object ReportingFormatter extends ReportingFormatter

trait ReportingFormatter {
  def getOnlineAdjustments(onlineAdjustments: Option[Boolean], adjustments: Option[Adjustments]): Option[String] = {
    onlineAdjustments match {
      case Some(false) => Some("No")
      case Some(true) =>
        adjustments.flatMap(_.onlineTests.map { onlineTests =>
          List(
            onlineTests.extraTimeNeeded.map(value => s"Verbal extra time needed: $value%"),
            onlineTests.extraTimeNeededNumerical.map(value => s"Numerical extra time needed: $value%"),
            onlineTests.otherInfo.map(value => s"$value")
          ).flatten.mkString(", ")
        }).orElse(Some("Yes"))
      case None => None
    }
  }

  def getAssessmentCentreAdjustments(assessmentCentreAdjustments: Option[Boolean], adjustments: Option[Adjustments]) = {
    assessmentCentreAdjustments match {
      case Some(false) => Some("No")
      case Some(true) =>
        adjustments.map(adjustmentsVal => {
          val specificAdjustments = adjustmentsVal.assessmentCenter.map { assessmentCenter =>
            List(
              assessmentCenter.extraTimeNeeded.map(value => s"Extra time needed: $value%"),
              assessmentCenter.otherInfo.map(value => s"$value")
            )
          }.getOrElse(List.empty)
          val typeOfAdjustments: List[Option[String]] = {
            val adjustmentsExcluded = List("onlineTestsTimeExtension", "onlineTestsOther", "assessmentCenterTimeExtension",
              "assessmentCenterOther")
            val relevantTypeOfAdjustments = adjustmentsVal.typeOfAdjustments.map { adjustments =>
              adjustments.filterNot(adjustment => adjustmentsExcluded.contains(adjustment))
            }.getOrElse(List.empty)
            relevantTypeOfAdjustments.map(Some(_))
          }
          val allAdjustments: List[Option[String]] = specificAdjustments ++ typeOfAdjustments
          allAdjustments.flatten.mkString(", ")
        }).orElse(Some("Yes"))
      case None => None
    }
  }
}
