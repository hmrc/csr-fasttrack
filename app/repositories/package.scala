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

import factories.DateTimeFactory
import model.CandidateScoresCommands.{ CandidateScoreFeedback, CandidateScores, CandidateScoresAndFeedback }
import model.Commands._
import model.EvaluationResults._
import model.FlagCandidatePersistedObject.FlagCandidate
import model.PassmarkPersistedObjects._
import model.PersistedObjects.{ ContactDetails, PersistedAnswer, _ }
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson._
import repositories.application._
import services.GBTimeZoneService
import services.reporting.SocioEconomicScoreCalculator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.language.postfixOps

package object repositories {
  private val timeZoneService = GBTimeZoneService
  private implicit val connection = {
    MongoDbConnection.mongoConnector.db
  }

  lazy val personalDetailsRepository = new PersonalDetailsMongoRepository()
  lazy val applicationRepository = new GeneralApplicationMongoRepository(timeZoneService)
  lazy val contactDetailsRepository = new ContactDetailsMongoRepository()
  lazy val mediaRepository = new MediaMongoRepository()
  lazy val assistanceDetailsRepository = new AssistanceDetailsMongoRepository()
  lazy val frameworkRepository = new FrameworkYamlRepository()
  lazy val frameworkPreferenceRepository = new FrameworkPreferenceMongoRepository()
  lazy val questionnaireRepository = new QuestionnaireMongoRepository(new SocioEconomicScoreCalculator {})
  lazy val onlineTestRepository = new OnlineTestMongoRepository(DateTimeFactory)
  lazy val onlineTestPDFReportRepository = new OnlineTestPDFReportMongoRepository()
  lazy val testReportRepository = new TestReportMongoRepository()
  lazy val passMarkSettingsRepository = new PassMarkSettingsMongoRepository()
  lazy val assessmentCentrePassMarkSettingsRepository = new AssessmentCentrePassMarkSettingsMongoRepository()
  lazy val applicationAssessmentRepository = new ApplicationAssessmentMongoRepository()
  lazy val candidateAllocationMongoRepository = new CandidateAllocationMongoRepository(DateTimeFactory)
  lazy val diagnosticReportRepository = new DiagnosticReportingMongoRepository
  lazy val applicationAssessmentScoresRepository = new ApplicationAssessmentScoresMongoRepository(DateTimeFactory)
  lazy val reportingRepository = new ReportingMongoRepository(timeZoneService)
  lazy val flagCandidateRepository = new FlagCandidateMongoRepository
  lazy val schoolsRepository = SchoolsCSVRepository

  /** Create indexes */
  Await.result(Future.sequence(List(
    onlineTestPDFReportRepository.collection.indexesManager.create(Index(Seq(("applicationId", Ascending)), unique = true)),
    applicationRepository.collection.indexesManager.create(Index(Seq(("applicationId", Ascending), ("userId", Ascending)), unique = true)),
    applicationRepository.collection.indexesManager.create(Index(Seq(("userId", Ascending), ("frameworkId", Ascending)), unique = true)),
    onlineTestRepository.collection.indexesManager.create(Index(Seq(("online-tests.token", Ascending)), unique = false)),
    onlineTestRepository.collection.indexesManager.create(Index(Seq(("applicationStatus", Ascending)), unique = false)),
    onlineTestRepository.collection.indexesManager.create(Index(Seq(("online-tests.invitationDate", Ascending)), unique = false)),

    contactDetailsRepository.collection.indexesManager.create(Index(Seq(("userId", Ascending)), unique = true)),
    passMarkSettingsRepository.collection.indexesManager.create(Index(Seq(("createDate", Ascending)), unique = true)),

    assessmentCentrePassMarkSettingsRepository.collection.indexesManager.create(Index(Seq(("info.createDate", Ascending)), unique = true)),

    applicationAssessmentRepository.collection.indexesManager.create(Index(Seq(("venue", Ascending), ("date", Ascending),
      ("session", Ascending), ("slot", Ascending)), unique = true)),
    applicationAssessmentRepository.collection.indexesManager.create(Index(Seq(("applicationId", Ascending)), unique = true)),

    applicationAssessmentScoresRepository.collection.indexesManager.create(Index(Seq(("applicationId", Ascending)), unique = true))
  )), 20 seconds)

  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(time: BSONDateTime) = new DateTime(time.value, DateTimeZone.UTC)

    def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
  }

  /** Implicit transformation for the DateTime **/
  implicit object BSONLocalDateHandler extends BSONHandler[BSONString, LocalDate] {
    def read(time: BSONString) = LocalDate.parse(time.value)

    def write(jdtime: LocalDate) = BSONString(jdtime.toString("yyyy-MM-dd"))
  }

  /** Implicit transformation for the Candidate **/
  implicit object BSONCandidateHandler extends BSONHandler[BSONDocument, Candidate] {
    def read(doc: BSONDocument): Candidate = {
      val userId = doc.getAs[String]("userId").getOrElse("")
      val applicationId = doc.getAs[String]("applicationId")

      val psRoot = doc.getAs[BSONDocument]("personal-details")
      val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
      val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
      val preferredName = psRoot.flatMap(_.getAs[String]("preferredName"))
      val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

      Candidate(userId, applicationId, None, firstName, lastName, preferredName, dateOfBirth, None, None)
    }

    def write(psDoc: Candidate) = BSONDocument() // this should not be used ever
  }

  implicit object BSONMapHandler extends BSONHandler[BSONDocument, Map[String, Int]] {
    override def write(map: Map[String, Int]): BSONDocument = {
      val elements = map.toStream.map { tuple =>
        tuple._1 -> BSONInteger(tuple._2)
      }
      BSONDocument(elements)
    }

    override def read(bson: BSONDocument): Map[String, Int] = {
      val elements = bson.elements.map { tuple =>
        // assume that all values in the document are BSONDocuments
        tuple._1 -> tuple._2.seeAsTry[Int].get
      }
      elements.toMap
    }
  }

  implicit val withdrawHandler: BSONHandler[BSONDocument, WithdrawApplicationRequest] = Macros.handler[WithdrawApplicationRequest]
  implicit val cdHandler: BSONHandler[BSONDocument, ContactDetails] = Macros.handler[ContactDetails]
  implicit val addressHandler: BSONHandler[BSONDocument, Address] = Macros.handler[Address]
  implicit val answerHandler: BSONHandler[BSONDocument, PersistedAnswer] = Macros.handler[PersistedAnswer]
  implicit val candidateScoresHandler: BSONHandler[BSONDocument, CandidateScores] = Macros.handler[CandidateScores]
  implicit val candidateScoreFeedback: BSONHandler[BSONDocument, CandidateScoreFeedback] = Macros.handler[CandidateScoreFeedback]
  implicit val candidateScoresAndFeedback: BSONHandler[BSONDocument, CandidateScoresAndFeedback] = Macros.handler[CandidateScoresAndFeedback]
  implicit val passMarkSchemeThresholdHandler: BSONHandler[BSONDocument, PassMarkSchemeThreshold] =
    Macros.handler[PassMarkSchemeThreshold]
  implicit val assessmentCentrePassMarkInfoHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkInfo] =
    Macros.handler[AssessmentCentrePassMarkInfo]
  implicit val assessmentCentrePassMarkSchemeHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkScheme] =
    Macros.handler[AssessmentCentrePassMarkScheme]
  implicit val assessmentCentrePassMarkSettingsHandler: BSONHandler[BSONDocument, AssessmentCentrePassMarkSettings] =
    Macros.handler[AssessmentCentrePassMarkSettings]
  implicit val competencyAverageResultHandler: BSONHandler[BSONDocument, CompetencyAverageResult] =
    Macros.handler[CompetencyAverageResult]
  implicit val flagCandidateHandler: BSONHandler[BSONDocument, FlagCandidate] = Macros.handler[FlagCandidate]
}
