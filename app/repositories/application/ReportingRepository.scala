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

import java.util.regex.Pattern

import common.Constants.{ No, Yes }
import model.{ ApplicationStatuses, _ }
import model.ApplicationStatusOrder.{ getStatus, _ }
import model.Commands._
import model.ReportExchangeObjects._
import model._
import model.commands.OnlineTestProgressResponse
import model.exchange.AssistanceDetails
import model.report.AdjustmentReportItem
import org.joda.time.LocalDate
import play.api.libs.json.Format
import reactivemongo.api.{ DB, ReadPreference }
import reactivemongo.bson.{ BSONArray, BSONDocument, BSONObjectID, BSONRegex, _ }
import repositories.{ CollectionNames, RandomSelection, ReactiveRepositoryHelpers, _ }
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * This class should contain those repo methods involving application collection that are related
 * exclusively to reporting functionality
 */
trait ReportingRepository {
  def applicationsForCandidateProgressReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]]

  def candidateProgressReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]]

  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]]

  def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]]

  def allApplicationAndUserIds(frameworkId: String): Future[List[ApplicationUserIdReport]]

  def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]]

  // The progress report contains common data for diversity
  def diversityReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]]

  // The progress report contains common data for pass mark
  def passMarkReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]]

  def assessmentCentreIndicatorReport: Future[List[AssessmentCentreIndicatorReport]]

  def applicationsForAssessmentScoresReport(frameworkId: String): Future[List[ApplicationForAssessmentScoresReport]]
}

class ReportingMongoRepository(timeZoneService: TimeZoneService)(implicit mongo: () => DB)
    extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
      Commands.Implicits.createApplicationRequestFormats, ReactiveMongoFormats.objectIdFormats)
    with ReportingRepository with RandomSelection with CommonBSONDocuments with ReportingRepoBSONReader
    with ReactiveRepositoryHelpers {

  def docToCandidate(doc: BSONDocument): Candidate = {
    val userId = doc.getAs[String]("userId").getOrElse("")
    val applicationId = doc.getAs[String]("applicationId")

    val psRoot = doc.getAs[BSONDocument]("personal-details")
    val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
    val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
    val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

    Candidate(userId, applicationId, None, firstName, lastName, Some(dateOfBirth.toString), None, None, None)
  }

  // scalastyle:off method.length
  private def findProgress(document: BSONDocument, applicationId: String): ProgressResponse = {

    (document.getAs[BSONDocument]("progress-status") map { root =>

      def getStatus(root: BSONDocument)(key: String) = {
        root.getAs[Boolean](key).getOrElse(false)
      }

      def getProgress = getStatus(root) _

      def getQuestionnaire = getStatus(root.getAs[BSONDocument]("questionnaire").getOrElse(BSONDocument())) _

      ProgressResponse(
        applicationId,
        personalDetails = getProgress(ProgressStatuses.PersonalDetailsCompletedProgress),
        hasSchemeLocations = getProgress(ProgressStatuses.SchemeLocationsCompletedProgress),
        hasSchemes = getProgress(ProgressStatuses.SchemesPreferencesCompletedProgress),
        assistanceDetails = getProgress(ProgressStatuses.AssistanceDetailsCompletedProgress),
        review = getProgress(ProgressStatuses.ReviewCompletedProgress),
        questionnaire = QuestionnaireProgressResponse(
          diversityStarted = getQuestionnaire(ProgressStatuses.StartDiversityQuestionnaireProgress),
          diversityCompleted = getQuestionnaire(ProgressStatuses.DiversityQuestionsCompletedProgress),
          educationCompleted = getQuestionnaire(ProgressStatuses.EducationQuestionsCompletedProgress),
          occupationCompleted = getQuestionnaire(ProgressStatuses.OccupationQuestionsCompletedProgress)
        ),
        submitted = getProgress(ProgressStatuses.SubmittedProgress),
        withdrawn = getProgress(ProgressStatuses.WithdrawnProgress),
        OnlineTestProgressResponse(
          invited = getProgress(ProgressStatuses.OnlineTestInvitedProgress),
          started = getProgress(ProgressStatuses.OnlineTestStartedProgress),
          completed = getProgress(ProgressStatuses.OnlineTestCompletedProgress),
          expired = getProgress(ProgressStatuses.OnlineTestExpiredProgress),
          awaitingReevaluation = getProgress(ProgressStatuses.AwaitingOnlineTestReevaluationProgress),
          failed = getProgress(ProgressStatuses.OnlineTestFailedProgress),
          failedNotified = getProgress(ProgressStatuses.OnlineTestFailedNotifiedProgress),
          awaitingAllocation = getProgress(ProgressStatuses.AwaitingAllocationProgress),
          awaitingAllocationNotified = getProgress(ProgressStatuses.AwaitingAllocationNotifiedProgress),
          allocationConfirmed = getProgress(ProgressStatuses.AllocationConfirmedProgress),
          allocationUnconfirmed = getProgress(ProgressStatuses.AllocationUnconfirmedProgress)
        ),
        failedToAttend = getProgress(ProgressStatuses.FailedToAttendProgress),
        assessmentScores = AssessmentScores(
          getProgress(ProgressStatuses.AssessmentScoresEnteredProgress),
          getProgress(ProgressStatuses.AssessmentScoresAcceptedProgress)
        ),
        assessmentCentre = AssessmentCentre(
          getProgress(ProgressStatuses.AwaitingAssessmentCentreReevaluationProgress),
          getProgress(ProgressStatuses.AssessmentCentrePassedProgress),
          getProgress(ProgressStatuses.AssessmentCentreFailedProgress),
          getProgress(ProgressStatuses.AssessmentCentrePassedNotifiedProgress),
          getProgress(ProgressStatuses.AssessmentCentreFailedNotifiedProgress)
        )
      )
    }).getOrElse(ProgressResponse(applicationId))
  }
  // scalastyle:on method.length

  def findByCriteria(
    firstOrPreferredNameOpt: Option[String],
    lastNameOpt: Option[String],
    dateOfBirthOpt: Option[LocalDate],
    filterToUserIds: List[String]
  ): Future[List[Candidate]] = {

    def matchIfSomeCaseInsensitive(value: Option[String]) = value.map(v => BSONRegex("^" + Pattern.quote(v) + "$", "i"))

    val innerQuery = BSONArray(
      BSONDocument("$or" -> BSONArray(
        BSONDocument("personal-details.firstName" -> matchIfSomeCaseInsensitive(firstOrPreferredNameOpt)),
        BSONDocument("personal-details.preferredName" -> matchIfSomeCaseInsensitive(firstOrPreferredNameOpt))
      )),
      BSONDocument("personal-details.lastName" -> matchIfSomeCaseInsensitive(lastNameOpt)),
      BSONDocument("personal-details.dateOfBirth" -> dateOfBirthOpt)
    )

    val fullQuery = if (filterToUserIds.isEmpty) {
      innerQuery
    } else {
      innerQuery ++ BSONDocument("userId" -> BSONDocument("$in" -> filterToUserIds))
    }

    val query = BSONDocument("$and" -> fullQuery)

    val projection = BSONDocument("userId" -> true, "applicationId" -> true, "applicationRoute" -> true,
      "applicationStatus" -> true, "personal-details" -> true)

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map(_.map(docToCandidate))
  }

  override def candidateProgressReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] =
    overallReportWithPersonalDetails(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Withdrawn))
    )))

  override def applicationsForCandidateProgressReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]] =
    applicationsForCandidateProgressReport(BSONDocument("frameworkId" -> frameworkId))

  private def applicationsForCandidateProgressReport(query: BSONDocument): Future[List[ApplicationForCandidateProgressReport]] = {
    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1",
      "progress-status" -> "1",
      "personal-details.civilServant" -> "1",
      "schemes" -> "1",
      "scheme-locations" -> "1",
      "assistance-details" -> "1",
      "assessment-centre-indicator" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForCandidateProgressReport](query, projection)
  }

  override def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]] =
    applicationPreferencesWithTestResults(BSONDocument("$and" -> BSONArray(
      BSONDocument("frameworkId" -> frameworkId),
      BSONDocument(s"progress-status.${ProgressStatuses.AssessmentCentrePassedProgress}" -> true),
      BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Withdrawn))
    )))

  private def applicationPreferencesWithTestResults(query: BSONDocument): Future[List[ApplicationPreferencesWithTestResults]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.aLevel" -> "1",
      "personal-details.stemLevel" -> "1",
      "personal-details.dateOfBirth" -> "1",
      "schemes" -> "1",
      "scheme-locations" -> "1",
      "assessment-centre-passmark-evaluation.competency-average" -> "1",
      "assessment-centre-passmark-evaluation.schemes-evaluation" -> "1",
      "assessment-centre-passmark-evaluation.overall-evaluation" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationPreferencesWithTestResults](query, projection)
  }

  private def overallReportWithPersonalDetails(query: BSONDocument): Future[List[ReportWithPersonalDetails]] = {
    val projection = BSONDocument(
      "userId" -> "1",
      "framework-preferences.alternatives.location" -> "1",
      "framework-preferences.alternatives.framework" -> "1",
      "framework-preferences.firstLocation.location" -> "1",
      "framework-preferences.secondLocation.location" -> "1",
      "framework-preferences.firstLocation.firstFramework" -> "1",
      "framework-preferences.secondLocation.firstFramework" -> "1",
      "framework-preferences.firstLocation.secondFramework" -> "1",
      "framework-preferences.secondLocation.secondFramework" -> "1",
      "personal-details.aLevel" -> "1",
      "personal-details.dateOfBirth" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.stemLevel" -> "1",
      "online-tests.cubiksUserId" -> "1",
      "assistance-details.hasDisability" -> "1",
      "assistance-details.needsSupportForOnlineAssessment" -> "1",
      "assistance-details.needsSupportAtVenue" -> "1",
      "assistance-details.guaranteedInterview" -> "1",
      "applicationId" -> "1",
      "progress-status" -> "2"
    )

    reportQueryWithProjections[BSONDocument](query, projection) map { lst =>
      lst.map(docToReportWithPersonalDetails)
    }
  }

  private def docToReportWithPersonalDetails(document: BSONDocument) = {
    val fr = document.getAs[BSONDocument]("framework-preferences")
    val fr1 = fr.flatMap(_.getAs[BSONDocument]("firstLocation"))
    val fr2 = fr.flatMap(_.getAs[BSONDocument]("secondLocation"))

    def frLocation(root: Option[BSONDocument]) = extract("location")(root)

    def frScheme1(root: Option[BSONDocument]) = extract("firstFramework")(root)

    def frScheme2(root: Option[BSONDocument]) = extract("secondFramework")(root)

    val personalDetails = document.getAs[BSONDocument]("personal-details")
    val aLevel = personalDetails.flatMap(_.getAs[Boolean]("aLevel").map(booleanTranslator))
    val stemLevel = personalDetails.flatMap(_.getAs[Boolean]("stemLevel").map(booleanTranslator))
    val firstName = personalDetails.flatMap(_.getAs[String]("firstName"))
    val lastName = personalDetails.flatMap(_.getAs[String]("lastName"))
    val preferredName = personalDetails.flatMap(_.getAs[String]("preferredName"))
    val dateOfBirth = personalDetails.flatMap(_.getAs[String]("dateOfBirth"))

    val fpAlternatives = fr.flatMap(_.getAs[BSONDocument]("alternatives"))
    val location = fpAlternatives.flatMap(_.getAs[Boolean]("location").map(booleanTranslator))
    val framework = fpAlternatives.flatMap(_.getAs[Boolean]("framework").map(booleanTranslator))

    val ad = document.getAs[BSONDocument]("assistance-details")
    val hasDisability = ad.flatMap(_.getAs[String]("hasDisability"))
    val needsSupportForOnlineAssessment = ad.flatMap(_.getAs[Boolean]("needsSupportForOnlineAssessment"))
    val needsSupportAtVenue = ad.flatMap(_.getAs[Boolean]("needsSupportAtVenue"))
    // TODO: Revisit this and check if we want to return both fields. If we want to keep one decide what to do if both values are None
    val needsAdjustment: Option[String] = (needsSupportForOnlineAssessment, needsSupportAtVenue) match {
      case (None, None) => None
      case (Some(true), _) => Some(Yes)
      case (_, Some(true)) => Some(Yes)
      case _ => Some(No)
    }
    val guaranteedInterview = ad.flatMap(_.getAs[Boolean]("guaranteedInterview")).map { gis => if (gis) Yes else No }

    val applicationId = document.getAs[String]("applicationId").getOrElse("")
    val userId = document.getAs[String]("userId").getOrElse("")
    val progress: ProgressResponse = findProgress(document, applicationId)

    val onlineTests = document.getAs[BSONDocument]("online-tests")
    val cubiksUserId = onlineTests.flatMap(_.getAs[Int]("cubiksUserId"))

    ReportWithPersonalDetails(
      UniqueIdentifier(applicationId), UniqueIdentifier(userId), Some(getStatus(progress)), frLocation(fr1), frScheme1(fr1), frScheme2(fr1),
      frLocation(fr2), frScheme1(fr2), frScheme2(fr2), aLevel,
      stemLevel, location, framework, hasDisability, needsAdjustment, guaranteedInterview, firstName, lastName,
      preferredName, dateOfBirth, cubiksUserId
    )
  }

  //scalastyle:off method.length
  def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] = {

    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Created)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.InProgress)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Withdrawn)),
        BSONDocument("$or" ->
          BSONArray(
            BSONDocument("assistance-details.needsSupportForOnlineAssessment" -> true),
            BSONDocument("assistance-details.needsSupportAtVenue" -> true),
            BSONDocument("assistance-details.guaranteedInterview" -> true),
            BSONDocument("assistance-details.adjustmentsConfirmed" -> true)
          ))
      ))

    val projection = BSONDocument(
      "userId" -> "1",
      "applicationId" -> "1",
      "applicationStatus" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "assistance-details" -> "3",
      "assessment-centre-indicator" -> "2"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val personalDetails = document.getAs[BSONDocument]("personal-details")
        val userId = document.getAs[String]("userId").getOrElse("")
        val applicationId = document.getAs[String]("applicationId")
        val applicationStatus = document.getAs[String]("applicationStatus")
        val firstName = extract("firstName")(personalDetails)
        val lastName = extract("lastName")(personalDetails)
        val preferredName = extract("preferredName")(personalDetails)

        val assistanceDetails = document.getAs[AssistanceDetails]("assistance-details")
        val assistance = document.getAs[BSONDocument]("assistance-details")
        val adjustmentsConfirmed = assistance.flatMap(_.getAs[Boolean]("adjustmentsConfirmed"))
        val adjustmentsComment = assistance.flatMap(_.getAs[String]("adjustmentsComment")).map(AdjustmentsComment(_))
        val onlineTests = assistance.flatMap(_.getAs[AdjustmentDetail]("onlineTests"))
        val assessmentCenter = assistance.flatMap(_.getAs[AdjustmentDetail]("assessmentCenter"))
        val typeOfAdjustments = assistance.flatMap(_.getAs[List[String]]("typeOfAdjustments"))

        val adjustments = adjustmentsConfirmed.flatMap { ac =>
          if (ac) Some(Adjustments(typeOfAdjustments, adjustmentsConfirmed, onlineTests, assessmentCenter)) else None
        }

        val assessmentCentreIndicator = document.getAs[AssessmentCentreIndicator]("assessment-centre-indicator")

        AdjustmentReportItem(
          userId,
          applicationId,
          firstName,
          lastName,
          preferredName,
          None,
          None,
          applicationStatus,
          assistanceDetails,
          adjustments,
          adjustmentsComment,
          assessmentCentreIndicator
        )
      }
    }
  }
  //scalastyle:on

  def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingAllocationNotified)
      ))

    val projection = BSONDocument(
      "userId" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1",
      "personal-details.preferredName" -> "1",
      "personal-details.dateOfBirth" -> "1",
      "assessment-centre-indicator.assessmentCentre" -> "1",
      "assistance-details.typeOfAdjustments" -> "1"
    )

    reportQueryWithProjections[BSONDocument](query, projection).map { list =>
      list.map { document =>

        val userId = document.getAs[String]("userId").get
        val personalDetails = document.getAs[BSONDocument]("personal-details").get
        val firstName = personalDetails.getAs[String]("firstName").get
        val lastName = personalDetails.getAs[String]("lastName").get
        val preferredName = personalDetails.getAs[String]("preferredName").get
        val dateOfBirth = personalDetails.getAs[LocalDate]("dateOfBirth").get
        val assessmentCentreIndicator = document.getAs[BSONDocument]("assessment-centre-indicator").get
        val assessmentCentreLocation = assessmentCentreIndicator.getAs[String]("assessmentCentre").get

        val assistance = document.getAs[BSONDocument]("assistance-details")
        val typeOfAdjustments = assistance.flatMap(_.getAs[List[String]]("typeOfAdjustments")).map(_.mkString("|"))

        CandidateAwaitingAllocation(userId, firstName, lastName, preferredName, dateOfBirth, typeOfAdjustments, assessmentCentreLocation)
      }
    }
  }

  private def reportQueryWithProjections[A](
    query: BSONDocument,
    prj: BSONDocument,
    upTo: Int = Int.MaxValue,
    stopOnError: Boolean = true
  )(implicit reader: Format[A]): Future[List[A]] =
    collection.find(query).projection(prj)
      .cursor[A](ReadPreference.nearest)
      .collect[List](upTo, stopOnError)

  private def reportQueryWithProjectionsBSON[A](
    query: BSONDocument,
    prj: BSONDocument,
    upTo: Int = Int.MaxValue,
    stopOnError: Boolean = true
  )(implicit reader: BSONDocumentReader[A]): Future[List[A]] =
    bsonCollection.find(query).projection(prj)
      .cursor[A](ReadPreference.nearest)
      .collect[List](Int.MaxValue, stopOnError = true)

  def extract(key: String)(root: Option[BSONDocument]) = root.flatMap(_.getAs[String](key))
  
  def allApplicationAndUserIds(frameworkId: String): Future[List[ApplicationUserIdReport]] = {
    val query = BSONDocument("frameworkId" -> frameworkId)
    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1"
    )

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map {
      _.map { doc =>
        val userId = doc.getAs[String]("userId").getOrElse("")
        val applicationId = doc.getAs[String]("applicationId").getOrElse("")
        ApplicationUserIdReport(UniqueIdentifier(applicationId), UniqueIdentifier(userId))
      }
    }
  }

  def diversityReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]] = {
    val query = BSONDocument(
      "frameworkId" -> frameworkId,
      "applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Withdrawn),
      "progress-status.questionnaire.diversity_questions_completed" -> BSONDocument("$exists" -> true)
    )

    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1",
      "progress-status" -> "1",
      "personal-details.civilServant" -> "1",
      "schemes" -> "1",
      "scheme-locations" -> "1",
      "assistance-details" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForCandidateProgressReport](query, projection)
  }

  def passMarkReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]] = {
    val query = BSONDocument(
      "frameworkId" -> frameworkId,
      "progress-status.online_test_completed" -> BSONDocument("$exists" -> true)
    )

    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1",
      "progress-status" -> "1",
      "personal-details.civilServant" -> "1",
      "schemes" -> "1",
      "scheme-locations" -> "1",
      "assistance-details" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForCandidateProgressReport](query, projection)
  }

  override def assessmentCentreIndicatorReport: Future[List[AssessmentCentreIndicatorReport]] = {
    val allApplicationsQuery = BSONDocument()

    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1",
      "applicationStatus" -> "1",
      "assessment-centre-indicator" -> "1"
    )

    reportQueryWithProjections[BSONDocument](allApplicationsQuery, projection).map { docs =>
      docs.map { doc =>
        val applicationId = doc.getAs[String]("applicationId")
        val userId = doc.getAs[String]("userId").get
        val applicationStatus = doc.getAs[String]("applicationStatus")
        val assessmentCentreIndicator = doc.getAs[AssessmentCentreIndicator]("assessment-centre-indicator")
        AssessmentCentreIndicatorReport(
          applicationId = applicationId,
          userId = userId,
          applicationStatus = applicationStatus,
          assessmentCentreIndicator = assessmentCentreIndicator
        )
      }
    }
  }

  override def applicationsForAssessmentScoresReport(frameworkId: String): Future[List[ApplicationForAssessmentScoresReport]] = {
    val query = BSONDocument("$and" ->
      BSONArray(
        BSONDocument("frameworkId" -> frameworkId),
        BSONDocument(s"progress-status.${ProgressStatuses.AllocationConfirmedProgress}" -> true)
      ))

    val projection = BSONDocument(
      "applicationId" -> "1",
      "userId" -> "1",
      "applicationStatus" -> "1",
      "assessment-centre-indicator" -> "1",
      "personal-details.firstName" -> "1",
      "personal-details.lastName" -> "1"
    )

    reportQueryWithProjectionsBSON[ApplicationForAssessmentScoresReport](query, projection)
  }
}
