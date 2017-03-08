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

import java.util.UUID
import java.util.regex.Pattern

import common.Constants.{ No, Yes }
import model.Adjustments._
import model.AssessmentScheduleCommands.{ ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult }
import model.Commands._
import model.EvaluationResults._
import model.Exceptions.{ ApplicationNotFound, CannotUpdateReview, LocationPreferencesNotFound, SchemePreferencesNotFound }
import model.Exceptions._
import model.PersistedObjects.ApplicationForNotification
import model.Scheme.Scheme
import model._
import model.commands.{ ApplicationStatusDetails, OnlineTestProgressResponse }
import model.exchange.AssistanceDetails
import model.persisted.SchemeEvaluationResult
import org.joda.time.format.DateTimeFormat
import org.joda.time.{ DateTime, LocalDate }
import play.api.libs.json.{ Format, JsNumber, JsObject }
import reactivemongo.api.{ DB, QueryOpts }
import reactivemongo.bson.{ BSONDocument, _ }
import reactivemongo.json.collection.JSONBatchCommands.JSONCountCommand
import repositories._
import services.TimeZoneService
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// scalastyle:off number.of.methods
trait GeneralApplicationRepository {

  def create(userId: String, frameworkId: String): Future[ApplicationResponse]

  def find(applicationId: String): Future[Option[Candidate]]

  def find(applicationIds: List[String]): Future[List[Candidate]]

  def findProgress(applicationId: String): Future[ProgressResponse]

  def findApplicationStatusDetails(applicationId: String): Future[ApplicationStatusDetails]

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse]

  def findCandidateByUserId(userId: String): Future[Option[Candidate]]

  def findByCriteria(firstOrPreferredName: Option[String], lastName: Option[String], dateOfBirth: Option[LocalDate],
    userIds: List[String] = List.empty): Future[List[Candidate]]

  def findApplicationIdsByLocation(location: String): Future[List[String]]

  def findApplicationsForAssessmentAllocation(locations: List[String], start: Int, end: Int): Future[ApplicationForAssessmentAllocationResult]

  def submit(applicationId: String): Future[Unit]

  def withdraw(applicationId: String, reason: WithdrawApplicationRequest): Future[Unit]

  def review(applicationId: String): Future[Unit]

  def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit]

  def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit]

  def findAdjustments(applicationId: String): Future[Option[Adjustments]]

  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit]

  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment]

  def removeAdjustmentsComment(applicationId: String): Future[Unit]

  def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]]

  def updateStatus(applicationId: String, status: ApplicationStatuses.EnumVal): Future[Unit]

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]]

  def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]]

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String, evaluationResult: AssessmentRuleCategoryResult,
                                    newApplicationStatus: ApplicationStatuses.EnumVal): Future[Unit]

  def getSchemeLocations(applicationId: String): Future[List[String]]

  def updateSchemeLocations(applicationId: String, locationIds: List[String]): Future[Unit]

  def updateAssessmentCentreIndicator(applicationId: String, indicator: AssessmentCentreIndicator): Future[Unit]

  def findAssessmentCentreIndicator(appId: String): Future[Option[AssessmentCentreIndicator]]

  def getSchemes(applicationId: String): Future[List[Scheme]]

  def updateSchemes(applicationId: String, schemeNames: List[Scheme]): Future[Unit]

  def removeSchemes(applicationId: String): Future[Unit]

  def removeSchemeLocations(applicationId: String): Future[Unit]

  def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit]
}

// scalastyle:on number.of.methods

// scalastyle:off number.of.methods
// scalastyle:off file.size.limit
class GeneralApplicationMongoRepository(timeZoneService: TimeZoneService)(implicit mongo: () => DB)
    extends ReactiveRepository[CreateApplicationRequest, BSONObjectID](CollectionNames.APPLICATION, mongo,
      Commands.Implicits.createApplicationRequestFormats,
      ReactiveMongoFormats.objectIdFormats) with GeneralApplicationRepository with RandomSelection with ReactiveRepositoryHelpers {

  override def create(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val applicationId = UUID.randomUUID().toString
    val applicationBSON = BSONDocument(
      "applicationId" -> applicationId,
      "userId" -> userId,
      "frameworkId" -> frameworkId,
      "applicationStatus" -> ApplicationStatuses.Created
    )

    collection.insert(applicationBSON) flatMap { _ =>
      findProgress(applicationId).map { p =>
        ApplicationResponse(applicationId, ApplicationStatuses.Created, userId, p)
      }
    }
  }

  def docToCandidate(doc: BSONDocument): Candidate = {
    val userId = doc.getAs[String]("userId").getOrElse("")
    val applicationId = doc.getAs[String]("applicationId")

    val psRoot = doc.getAs[BSONDocument]("personal-details")
    val firstName = psRoot.flatMap(_.getAs[String]("firstName"))
    val lastName = psRoot.flatMap(_.getAs[String]("lastName"))
    val preferredName = psRoot.flatMap(_.getAs[String]("preferredName"))
    val dateOfBirth = psRoot.flatMap(_.getAs[LocalDate]("dateOfBirth"))

    Candidate(userId, applicationId, None, firstName, lastName, preferredName, dateOfBirth, None, None)
  }

  def find(applicationIds: List[String]): Future[List[Candidate]] = {

    val query = BSONDocument("applicationId" -> BSONDocument("$in" -> applicationIds))

    collection.find(query).cursor[BSONDocument]().collect[List]().map(_.map(docToCandidate))
  }

  // scalastyle:off method.length
  private def findProgress(document: BSONDocument, applicationId: String): ProgressResponse = {

    (document.getAs[BSONDocument]("progress-status") map { root =>

      def getStatus(root: BSONDocument)(key: String) = {
        root.getAs[Boolean](key).getOrElse(false)
      }

      def getProgress = getStatus(root)_
      def getQuestionnaire = getStatus(root.getAs[BSONDocument]("questionnaire").getOrElse(BSONDocument()))_

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

  override def findProgress(applicationId: String): Future[ProgressResponse] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("progress-status" -> 2, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) => findProgress(document, applicationId)
      case None => throw ApplicationNotFound(applicationId)
    }
  }

  override def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = {
    require(progressStatuses.nonEmpty, "Progress statuses to remove cannot be empty")

    val query = BSONDocument("applicationId" -> applicationId)

    val statusesToUnset = progressStatuses.flatMap { progressStatus =>
      Map(
        s"progress-status.${progressStatus.name}" -> BSONString(""),
        s"progress-status-timestamp.${progressStatus.name}" -> BSONString("")
      )
    }

    val unsetDoc = BSONDocument("$unset" -> BSONDocument(statusesToUnset))

    val validator = singleUpdateValidator(applicationId, actionDesc = "removing progress and app status")

    collection.update(query, unsetDoc) map validator
  }

  def findApplicationStatusDetails(applicationId: String): Future[ApplicationStatusDetails] = {

    findProgress(applicationId).flatMap { progress =>
      val latestProgress = ApplicationStatusOrder.getStatus(progress)
      val query = BSONDocument("applicationId" -> applicationId)
      val projection = BSONDocument(
        "applicationStatus" -> true,
        "progress-status-timestamp" -> true,
        "submissionDeadline" -> true,
        "_id" -> false
      )

      val statusToGetDateFor = if ((latestProgress isAfter ProgressStatuses.PersonalDetailsCompletedProgress) &&
        (latestProgress isBefore ProgressStatuses.SubmittedProgress)) {
        ProgressStatuses.PersonalDetailsCompletedProgress
      } else { latestProgress }

      collection.find(query, projection).one[BSONDocument] map {
        case Some(document) =>
          val applicationStatus = document.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
          val progressStatusTimeStamp = document.getAs[BSONDocument]("progress-status-timestamp").flatMap(_.getAs[DateTime](statusToGetDateFor))
          val submissionDeadline = document.getAs[DateTime]("submissionDeadline")
          ApplicationStatusDetails(applicationStatus, progressStatusTimeStamp, submissionDeadline)

        case None => throw ApplicationNotFound(applicationId)
      }
    }
  }

  def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = {
    val query = BSONDocument("userId" -> userId, "frameworkId" -> frameworkId)

    val resp: Future[Future[ApplicationResponse]] = collection.find(query).one[BSONDocument] map {
      case Some(document) =>
        val applicationId = document.getAs[String]("applicationId").get
        val applicationStatus = document.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
        findProgress(applicationId).map { (p: ProgressResponse) =>
          ApplicationResponse(applicationId, applicationStatus, userId, p)
        }
      case None => throw ApplicationNotFound(userId)
    }
    resp.flatMap(identity)
  }

  def find(applicationId: String): Future[Option[Candidate]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    collection.find(query).one[BSONDocument].map(_.map(docToCandidate))
  }

  def findCandidateByUserId(userId: String): Future[Option[Candidate]] = {
    val query = BSONDocument("userId" -> userId)
    collection.find(query).one[BSONDocument].map(_.map(docToCandidate))
  }

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

  override def findApplicationIdsByLocation(location: String): Future[List[String]] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("$and" -> BSONArray(
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Created)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.Withdrawn)),
        BSONDocument("applicationStatus" -> BSONDocument("$ne" -> ApplicationStatuses.InProgress))
      )),
      BSONDocument("$or" -> BSONArray(
        BSONDocument("framework-preferences.firstLocation.location" -> location),
        BSONDocument("framework-preferences.secondLocation.location" -> location)
      ))
    ))

    val projection = BSONDocument("applicationId" -> 1)

    collection.find(query, projection).cursor[BSONDocument]().collect[List]().map { docList =>
      docList.map { doc =>
        doc.getAs[String]("applicationId").get
      }
    }
  }

  override def findApplicationsForAssessmentAllocation(locations: List[String], start: Int,
    end: Int): Future[ApplicationForAssessmentAllocationResult] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingAllocationNotified),
      BSONDocument("assessment-centre-indicator.assessmentCentre" -> BSONDocument("$in" -> locations))
    ))

    collection.runCommand(JSONCountCommand.Count(query)).flatMap { c =>
      val count = c.count

      if (count == 0) {
        Future.successful(ApplicationForAssessmentAllocationResult(List.empty, 0))
      } else {
        val projection = BSONDocument(
          "userId" -> true,
          "applicationId" -> true,
          "personal-details.firstName" -> true,
          "personal-details.lastName" -> true,
          "personal-details.dateOfBirth" -> true,
          "assistance-details.needsSupportAtVenue" -> true,
          "online-tests.invitationDate" -> true
        )
        val sort = new JsObject(Map("online-tests.invitationDate" -> JsNumber(1)))

        collection.find(query, projection).sort(sort).options(QueryOpts(skipN = start)).cursor[BSONDocument]().collect[List](end - start + 1).
          map { docList =>
            docList.map { doc =>
              bsonDocToApplicationsForAssessmentAllocation(doc)
            }
          }.flatMap { result =>
            Future.successful(ApplicationForAssessmentAllocationResult(result, count))
          }
      }
    }
  }

  override def submit(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> ApplicationStatuses.Submitted,
      s"progress-status.${ProgressStatuses.SubmittedProgress}" -> true,
      s"progress-status-timestamp.${ProgressStatuses.SubmittedProgress}" -> DateTime.now()
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { _ => () }
  }

  override def withdraw(applicationId: String, reason: WithdrawApplicationRequest): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      "withdraw" -> reason,
      "applicationStatus" -> ApplicationStatuses.Withdrawn,
      s"progress-status.${ProgressStatuses.WithdrawnProgress}" -> true,
      s"progress-status-timestamp.${ProgressStatuses.WithdrawnProgress}" -> DateTime.now()
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { _ => () }
  }

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.questionnaire.$sectionKey" -> true
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { _ => () }
  }

  override def review(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val progressStatusBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.${ProgressStatuses.ReviewCompletedProgress}" -> true,
      s"progress-status-timestamp.${ProgressStatuses.ReviewCompletedProgress}" -> DateTime.now
    ))

    val validator = singleUpdateValidator(applicationId, actionDesc = "review",
      CannotUpdateReview(s"review $applicationId"))

    collection.update(query, progressStatusBSON) map validator
  }

  def extract(key: String)(root: Option[BSONDocument]) = root.flatMap(_.getAs[String](key))

  def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit] = {

    val query = BSONDocument("applicationId" -> applicationId)

    val resetAdjustmentsBSON = BSONDocument("$unset" -> BSONDocument(
      "assistance-details.onlineTests" -> "",
      "assistance-details.assessmentCenter" -> ""
    ))

    val adjustmentsConfirmationBSON = BSONDocument("$set" -> BSONDocument(
      "assistance-details.typeOfAdjustments" -> data.typeOfAdjustments.getOrElse(List.empty[String]),
      "assistance-details.adjustmentsConfirmed" -> true,
      "assistance-details.onlineTests" -> data.onlineTests,
      "assistance-details.assessmentCenter" -> data.assessmentCenter
    ))

    val resetValidator = singleUpdateValidator(applicationId, actionDesc = "reset")
    val adjustmentValidator = singleUpdateValidator(applicationId, actionDesc = "updateAdjustments")

    collection.update(query, resetAdjustmentsBSON).map(resetValidator).flatMap { _ =>
      collection.update(query, adjustmentsConfirmationBSON) map adjustmentValidator
    }
  }

  def findAdjustments(applicationId: String): Future[Option[Adjustments]] = {

    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("assistance-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument].map {
      _.flatMap { document =>
        val rootOpt = document.getAs[BSONDocument]("assistance-details")
        rootOpt.map { root =>
          val adjustmentList = root.getAs[List[String]]("typeOfAdjustments")
          val adjustmentsConfirmed = root.getAs[Boolean]("adjustmentsConfirmed")
          val onlineTests = root.getAs[AdjustmentDetail]("onlineTests")
          val assessmentCenter = root.getAs[AdjustmentDetail]("assessmentCenter")
          Adjustments(adjustmentList, adjustmentsConfirmed, onlineTests, assessmentCenter)
        }
      }
    }
  }

  def removeAdjustmentsComment(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val removeBSON = BSONDocument("$unset" -> BSONDocument(
      "assistance-details.adjustmentsComment" -> ""
    ))

    val validator = singleUpdateValidator(
      applicationId,
      actionDesc = "remove adjustments comment",
      notFound = CannotRemoveAdjustmentsComment(applicationId)
    )

    collection.update(query, removeBSON) map validator
  }

  def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)

    val updateBSON = BSONDocument("$set" -> BSONDocument(
      "assistance-details.adjustmentsComment" -> adjustmentsComment.comment
    ))

    val validator = singleUpdateValidator(
      applicationId,
      actionDesc = "save adjustments comment",
      notFound = CannotUpdateAdjustmentsComment(applicationId)
    )

    collection.update(query, updateBSON) map validator
  }

  def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("assistance-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument].map {
      case Some(document) =>
        val root = document.getAs[BSONDocument]("assistance-details")
        root match {
          case Some(doc) =>
            doc.getAs[String]("adjustmentsComment") match {
              case Some(comment) => AdjustmentsComment(comment)
              case None => throw AdjustmentsCommentNotFound(applicationId)
            }
          case None => throw AdjustmentsCommentNotFound(applicationId)
        }
      case None => throw ApplicationNotFound(applicationId)
    }
  }

  def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val format = DateTimeFormat.forPattern("yyyy-MM-dd")
    val projection = BSONDocument(
      "allocation-expire-date" -> "1"
    )

    collection.find(query, projection).one[BSONDocument].map {
      _.flatMap { doc =>
        doc.getAs[String]("allocation-expire-date").map(d => format.parseDateTime(d).toLocalDate)
      }
    }
  }

  def updateStatus(applicationId: String, status: ApplicationStatuses.EnumVal): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val lowercaseStatus = status.toLowerCase
    val applicationStatusBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> status,
      s"progress-status.$lowercaseStatus" -> true,
      s"progress-status-dates.$lowercaseStatus" -> LocalDate.now()
    ))

    collection.update(query, applicationStatusBSON, upsert = false) map { _ => () }
  }

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]] = {
    val query =
      BSONDocument("$or" ->
        BSONArray(
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted),
              BSONDocument("assessment-centre-passmark-evaluation.passmarkVersion" -> BSONDocument("$exists" -> false))
            )
          ),
          BSONDocument(
            "$and" -> BSONArray(
              BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingAssessmentCentreReevaluation),
              BSONDocument("assessment-centre-passmark-evaluation.passmarkVersion" -> BSONDocument("$ne" -> currentPassmarkVersion))
            )
          )
        ))

    selectRandom(query).map(_.map(doc => doc.getAs[String]("applicationId").get))
  }

  def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]] = {
    val query = BSONDocument(
      "$and" -> BSONArray(
        BSONDocument("online-tests.pdfReportSaved" -> true),
        BSONDocument(
          "$or" -> BSONArray(
            BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentCentrePassed),
            BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentCentreFailed)
          )
        )
      )
    )
    selectRandom(query).map(_.map(bsonDocToApplicationForNotification))
  }

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String, evaluationResult: AssessmentRuleCategoryResult,
                                    newApplicationStatus: ApplicationStatuses.EnumVal): Future[Unit] = {
    val query = BSONDocument("$and" -> BSONArray(
      BSONDocument("applicationId" -> applicationId),
      BSONDocument(
        "$or" -> BSONArray(
          BSONDocument("applicationStatus" -> ApplicationStatuses.AssessmentScoresAccepted),
          BSONDocument("applicationStatus" -> ApplicationStatuses.AwaitingAssessmentCentreReevaluation)
        )
      )
    ))
    val progressStatus = newApplicationStatus.name.toLowerCase

    val passMarkEvaluation = BSONDocument("$set" ->
      BSONDocument(
        "applicationStatus" -> newApplicationStatus,
        s"progress-status.$progressStatus" -> true,
        s"progress-status-dates.$progressStatus" -> LocalDate.now(),
        "assessment-centre-passmark-evaluation" -> BSONDocument("passmarkVersion" -> passmarkVersion)
          .add(booleanToBSON("passedMinimumCompetencyLevel", evaluationResult.passedMinimumCompetencyLevel))
          .add(BSONDocument("competency-average" -> evaluationResult.competencyAverageResult))
          .add(saveSchemes("schemes-evaluation", evaluationResult.schemesEvaluation))
          .add(saveSchemes("overall-evaluation", evaluationResult.overallEvaluation))
      ))

    collection.update(query, passMarkEvaluation, upsert = false) map { _ => () }
  }

  private def saveSchemes(name: String, schemes: List[PerSchemeEvaluation]): BSONDocument = {
    val mySchemes = schemes.map(x => BSONDocument(x.schemeName -> x.result.toString))
    val schemesDoc = mySchemes.foldRight(BSONDocument.empty)((acc, doc) => acc.add(doc))
    BSONDocument(name -> schemesDoc)
  }

  def getSchemeLocations(applicationId: String): Future[List[String]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("scheme-locations" -> 1)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[List[String]]("scheme-locations").isDefined =>
        document.getAs[List[String]]("scheme-locations").get
      case _ => throw LocationPreferencesNotFound(applicationId)
    }
  }

  override def findAssessmentCentreIndicator(appId: String): Future[Option[AssessmentCentreIndicator]] = {
    val query = BSONDocument("applicationId" -> appId)
    val projection = BSONDocument(
      "_id" -> false,
      "assessment-centre-indicator" -> true
    )

    collection.find(query, projection).one[BSONDocument] map {
      case Some(doc) => doc.getAs[AssessmentCentreIndicator]("assessment-centre-indicator")
      case _ => None
    }
  }

  override def updateAssessmentCentreIndicator(appId: String, indicator: AssessmentCentreIndicator): Future[Unit] = {
    val query = BSONDocument("applicationId" -> appId)
    val update = BSONDocument("$set" -> BSONDocument(
      "assessment-centre-indicator" -> indicator
    ))

    collection.update(query, update, upsert = false) map { _ => () }
  }

  def updateSchemeLocations(applicationId: String, locationIds: List[String]): Future[Unit] = {
    require(locationIds.nonEmpty, "Scheme location preferences cannot be empty")

    val query = BSONDocument("applicationId" -> applicationId)
    val schemeLocationsBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.${ProgressStatuses.SchemeLocationsCompletedProgress}" -> true,
      "scheme-locations" -> locationIds
    ))
    collection.update(query, schemeLocationsBSON, upsert = false) map { _ => () }
  }

  def getSchemes(applicationId: String): Future[List[Scheme]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("schemes" -> 1)

    collection.find(query, projection).one[BSONDocument] map {
      case Some(document) if document.getAs[List[Scheme]]("schemes").isDefined =>
        document.getAs[List[Scheme]]("schemes").get
      case _ => throw SchemePreferencesNotFound(applicationId)
    }
  }

  def updateSchemes(applicationId: String, schemeNames: List[Scheme]): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val schemePreferencesBSON = BSONDocument("$set" -> BSONDocument(
      s"progress-status.${ProgressStatuses.SchemesPreferencesCompletedProgress}" -> true,
      "schemes" -> schemeNames
    ))
    collection.update(query, schemePreferencesBSON, upsert = false) map { _ => () }
  }

  override def removeSchemes(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val schemePreferencesBSON = BSONDocument("$unset" -> BSONDocument(
      s"progress-status.${ProgressStatuses.SchemesPreferencesCompletedProgress}" -> BSONString(""),
      "schemes" -> BSONString("")
    ))
    collection.update(query, schemePreferencesBSON, upsert = false) map { _ => () }
  }

  override def removeSchemeLocations(applicationId: String): Future[Unit] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val schemeLocationsBSON = BSONDocument("$unset" -> BSONDocument(
      s"progress-status.${ProgressStatuses.SchemeLocationsCompletedProgress}" -> BSONString(""),
      "scheme-locations" -> BSONString("")
    ))
    collection.update(query, schemeLocationsBSON, upsert = false) map { _ => () }
  }

  private def resultToBSON(schemeName: String, result: Option[EvaluationResults.Result]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r.toString)
    case _ => BSONDocument.empty
  }

  private def booleanToBSON(schemeName: String, result: Option[Boolean]): BSONDocument = result match {
    case Some(r) => BSONDocument(schemeName -> r)
    case _ => BSONDocument.empty
  }

  private def averageToBSON(name: String, result: Option[CompetencyAverageResult]): BSONDocument = result match {
    case Some(r) => BSONDocument(name -> r)
    case _ => BSONDocument.empty
  }

  private def perSchemeToBSON(name: String, result: Option[List[SchemeEvaluationResult]]): BSONDocument = result match {
    case Some(m) =>
      val schemes = m.map(x => BSONDocument(x.scheme.toString -> x.result.toString))
      val schemesDoc = schemes.foldRight(BSONDocument.empty)((acc, doc) => acc.add(doc))
      BSONDocument(name -> schemesDoc)
    case _ => BSONDocument.empty
  }

  private def bsonDocToApplicationsForAssessmentAllocation(doc: BSONDocument) = {
    val userId = doc.getAs[String]("userId").get
    val applicationId = doc.getAs[String]("applicationId").get
    val personalDetails = doc.getAs[BSONDocument]("personal-details").get
    val firstName = personalDetails.getAs[String]("firstName").get
    val lastName = personalDetails.getAs[String]("lastName").get
    val birthYear = personalDetails.getAs[LocalDate]("dateOfBirth").get.getYear
    val needsAdjustment = doc.getAs[BSONDocument]("assistance-details").flatMap(_.getAs[Boolean]("needsSupportAtVenue")).getOrElse(false)
    val onlineTestDetails = doc.getAs[BSONDocument]("online-tests").get
    val invitationDate = onlineTestDetails.getAs[DateTime]("invitationDate").get

    ApplicationForAssessmentAllocation(firstName, lastName, userId, applicationId, needsAdjustment, invitationDate, birthYear)
  }

  private def bsonDocToApplicationForNotification(doc: BSONDocument) = {
    val applicationId = doc.getAs[String]("applicationId").get
    val userId = doc.getAs[String]("userId").get
    val applicationStatus = doc.getAs[ApplicationStatuses.EnumVal]("applicationStatus").get
    val personalDetailsRoot = doc.getAs[BSONDocument]("personal-details").get
    val preferredName = personalDetailsRoot.getAs[String]("preferredName").get
    ApplicationForNotification(applicationId, userId, preferredName, applicationStatus)
  }
}
