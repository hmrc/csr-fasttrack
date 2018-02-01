/*
 * Copyright 2018 HM Revenue & Customs
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

import model.ApplicationStatusOrder.getStatus
import model.Commands._
import model.ReportExchangeObjects._
import model.Scheme.Scheme
import model.exchange.AssistanceDetails
import model._
import model.persisted.SchemeEvaluationResult
import org.joda.time.LocalDate
import reactivemongo.bson._
import repositories._

trait ReportingRepoBSONReader extends CommonBSONDocuments with BaseBSONReader {

  implicit val toLatestProgressStatus = bsonReader {
    (doc: BSONDocument) =>
      val progress: ProgressResponse = toProgressResponse("").read(doc)
      LatestProgressStatus(getStatus(progress))
  }

  implicit val toApplicationForCandidateProgressReport = bsonReader {
    (doc: BSONDocument) =>
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

  implicit val toApplicationForAssessmentScoresReport = bsonReader {
    (doc: BSONDocument) =>
      val applicationId = doc.getAs[String]("applicationId")
      val userId = doc.getAs[String]("userId").get
      val applicationStatus = doc.getAs[String]("applicationStatus").get
      val assessmentCentreIndicator = doc.getAs[AssessmentCentreIndicator]("assessment-centre-indicator")
      val pdDoc = doc.getAs[BSONDocument]("personal-details")
      val firstName = pdDoc.flatMap(_.getAs[String]("firstName"))
      val lastName = pdDoc.flatMap(_.getAs[String]("lastName"))

      ApplicationForAssessmentScoresReport(
        applicationId = applicationId,
        userId = userId,
        applicationStatus = applicationStatus,
        assessmentCentreIndicator = assessmentCentreIndicator,
        firstName = firstName,
        lastName = lastName
      )
  }

  implicit val toApplicationPreferencesWithTestResults = bsonReader {
    (doc: BSONDocument) =>

      def evaluation(key: String, evaluationMapDoc: Option[BSONDocument]) = {
        val evaluation = evaluationMapDoc.flatMap(_.getAs[List[SchemeEvaluationResult]](key)).getOrElse(List.empty)

        val schemesEvaluationMap = Scheme.AllSchemes.flatMap { scheme =>
          evaluation.find(_.scheme == scheme).map {
            scheme -> _.result.toPassmark
          }
        }.toMap

        val commercial = schemesEvaluationMap.get(Scheme.Commercial)
        val digitalAndTechnology = schemesEvaluationMap.get(Scheme.DigitalAndTechnology)
        val business = schemesEvaluationMap.get(Scheme.Business)
        val projectDelivery = schemesEvaluationMap.get(Scheme.ProjectDelivery)
        val finance = schemesEvaluationMap.get(Scheme.Finance)
        val policy = schemesEvaluationMap.get(Scheme.Policy)
        SchemeEvaluation(commercial, digitalAndTechnology, business, projectDelivery, finance, policy)
      }

      val applicationId = doc.getAs[String]("applicationId").get
      val userId = doc.getAs[String]("userId").get

      val personalDetails = doc.getAs[BSONDocument]("personal-details")
      val firstName = personalDetails.flatMap(_.getAs[String]("firstName"))
      val lastName = personalDetails.flatMap(_.getAs[String]("lastName"))
      val preferredName = personalDetails.flatMap(_.getAs[String]("preferredName"))
      val aLevel = personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
      val stemLevel = personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))
      val dateOfBirth = personalDetails.flatMap(_.getAs[LocalDate]("dateOfBirth"))

      val schemes = doc.getAs[List[Scheme]]("schemes").getOrElse(List.empty)
      val schemeLocations = doc.getAs[List[String]]("scheme-locations").getOrElse(List.empty)

      val passmarkEvaluationDoc = doc.getAs[BSONDocument]("assessment-centre-passmark-evaluation")

      val competencyAverage = passmarkEvaluationDoc.flatMap(_.getAs[BSONDocument]("competency-average"))
      val leadingAndCommunicatingAverage = competencyAverage.flatMap(_.getAs[Double]("leadingAndCommunicatingAverage"))
      val collaboratingAndPartneringAverage = competencyAverage.flatMap(_.getAs[Double]("collaboratingAndPartneringAverage"))
      val deliveringAtPaceAverage = competencyAverage.flatMap(_.getAs[Double]("deliveringAtPaceAverage"))
      val makingEffectiveDecisionsAverage = competencyAverage.flatMap(_.getAs[Double]("makingEffectiveDecisionsAverage"))
      val changingAndImprovingAverage = competencyAverage.flatMap(_.getAs[Double]("changingAndImprovingAverage"))
      val buildingCapabilityForAllAverage = competencyAverage.flatMap(_.getAs[Double]("buildingCapabilityForAllAverage"))
      val motivationFitAverage = competencyAverage.flatMap(_.getAs[Double]("motivationFitAverage"))
      val overallScore = competencyAverage.flatMap(_.getAs[Double]("overallScore"))

      val schemesEvaluation = evaluation("schemes-evaluation", passmarkEvaluationDoc)
      val overallEvaluation = evaluation("overall-evaluation", passmarkEvaluationDoc)

      ApplicationPreferencesWithTestResults(
        UniqueIdentifier(userId),
        UniqueIdentifier(applicationId),
        schemes,
        schemeLocations,
        PersonalInfo(firstName, lastName, preferredName, aLevel, stemLevel, dateOfBirth),
        CandidateScoresSummary(leadingAndCommunicatingAverage, collaboratingAndPartneringAverage,
          deliveringAtPaceAverage, makingEffectiveDecisionsAverage, changingAndImprovingAverage,
          buildingCapabilityForAllAverage, motivationFitAverage, overallScore),
        schemesEvaluation, overallEvaluation)
  }
}
