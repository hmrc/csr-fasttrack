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

package repositories.application

import common.Constants.{ No, Yes }
import model.ApplicationStatusOrder.getStatus
import model.Commands._
import model.ReportExchangeObjects.ApplicationForCandidateProgressReport
import model.Scheme.Scheme
import model.{ Adjustments, AssessmentCentreIndicator, UniqueIdentifier }
import model.exchange.AssistanceDetails
import reactivemongo.bson.{ BSONDocument, _ }
import repositories.{ BaseBSONReader, CommonBSONDocuments }

trait ReportingRepoBSONReader extends CommonBSONDocuments with BaseBSONReader {
  implicit val toApplicationForCandidateProgressReport = bsonReader {
    (doc: BSONDocument) => {
      val applicationId = doc.getAs[String]("applicationId").getOrElse("")
      val userId = doc.getAs[String]("userId").getOrElse("")
      val progress: ProgressResponse = toProgressResponse(applicationId).read(doc)
      val schemes = doc.getAs[List[Scheme]]("schemes").getOrElse(List.empty)
      val schemeLocations = doc.getAs[List[String]]("scheme-locations").getOrElse(List.empty)

      val assistanceDetails = doc.getAs[AssistanceDetails]("assistance-details")
      val adjustments = doc.getAs[Adjustments]("assistance-details")

      val pdDoc = doc.getAs[BSONDocument]("personal-details")
      val civilServant = pdDoc.flatMap(_.getAs[Boolean]("civilServant"))

      val assessmentCentreIndicator = doc.getAs[AssessmentCentreIndicator]("assessment-centre-indicator")

      ApplicationForCandidateProgressReport(
        Some(UniqueIdentifier(applicationId)), UniqueIdentifier(userId), Some(getStatus(progress)), schemes, schemeLocations,
        assistanceDetails.map(_.hasDisability), assistanceDetails.flatMap(_.guaranteedInterview),
        assistanceDetails.map(_.needsSupportForOnlineAssessment), assistanceDetails.map(_.needsSupportAtVenue),
        adjustments, civilServant, assessmentCentreIndicator
      )
    }
  }
}
