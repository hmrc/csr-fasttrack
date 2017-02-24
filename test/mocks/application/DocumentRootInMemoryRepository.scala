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

package mocks.application

import common.Constants.{ No, Yes }
import model.{ Adjustments, AdjustmentsComment, ApplicationStatuses, ProgressStatuses }
import model.AssessmentScheduleCommands.{ ApplicationForAssessmentAllocation, ApplicationForAssessmentAllocationResult }
import model.Commands._
import model.EvaluationResults.AssessmentRuleCategoryResult
import model.Exceptions.ApplicationNotFound
import model.PersistedObjects.ApplicationForNotification
import model.Scheme.Scheme
import model.commands.ApplicationStatusDetails
import org.joda.time.{ DateTime, LocalDate }
import repositories.application.GeneralApplicationRepository

import scala.collection.mutable
import scala.concurrent.Future
object DocumentRootInMemoryRepository extends DocumentRootInMemoryRepository

/**
 * @deprecated Please use Mockito
 */
// scalastyle:off number.of.methods
class DocumentRootInMemoryRepository extends GeneralApplicationRepository {

  override def find(applicationIds: List[String]): Future[List[Candidate]] = ???

  override def create(userId: String, frameworkId: String): Future[ApplicationResponse] = {

    val applicationId = java.util.UUID.randomUUID().toString
    val applicationCreated = ApplicationResponse(applicationId, ApplicationStatuses.Created, userId,
      ProgressResponse(applicationId))

    inMemoryRepo += applicationId -> applicationCreated
    Future.successful(applicationCreated)
  }

  override def findProgress(applicationId: String): Future[ProgressResponse] = applicationId match {
    case "1111-1234" => Future.failed(ApplicationNotFound(applicationId))
    case _ => Future.successful(ProgressResponse(applicationId, personalDetails = true))
  }

  val inMemoryRepo = new mutable.HashMap[String, ApplicationResponse]

  override def findByUserId(userId: String, frameworkId: String): Future[ApplicationResponse] = userId match {
    case "invalidUser" => Future.failed(ApplicationNotFound("invalidUser"))
    case _ =>
      val applicationId = "1111-1111"
      val applicationCreated = ApplicationResponse(applicationId, ApplicationStatuses.Created, userId,
        ProgressResponse(applicationId))
      Future.successful(applicationCreated)
  }

  override def findApplicationsForAssessmentAllocation(locations: List[String], start: Int,
    end: Int): Future[ApplicationForAssessmentAllocationResult] = {
    Future.successful(ApplicationForAssessmentAllocationResult(List(ApplicationForAssessmentAllocation("firstName", "lastName", "userId1",
      "applicationId1", needsAdjustment = false, DateTime.now, 1988)), 1))
  }

  override def submit(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def withdraw(applicationId: String, reason: WithdrawApplicationRequest): Future[Unit] = Future.successful(Unit)

  override def updateQuestionnaireStatus(applicationId: String, sectionKey: String): Future[Unit] = Future.successful(Unit)

  override def review(applicationId: String): Future[Unit] = Future.successful(Unit)

  override def findByCriteria(firstOrPreferredName: Option[String], lastName: Option[String],
    dateOfBirth: Option[LocalDate], userIds: List[String]): Future[List[Candidate]] =
    Future.successful(List.empty[Candidate])

  override def findCandidateByUserId(userId: String): Future[Option[Candidate]] = Future.successful(None)

  override def findApplicationIdsByLocation(location: String): Future[List[String]] = Future.successful(List())

  override def confirmAdjustments(applicationId: String, data: Adjustments): Future[Unit] = Future.successful(Unit)

  override def allocationExpireDateByApplicationId(applicationId: String): Future[Option[LocalDate]] = ???

  override def updateStatus(applicationId: String, status: ApplicationStatuses.EnumVal): Future[Unit] = ???

  override def nextAssessmentCentrePassedOrFailedApplication(): Future[Option[ApplicationForNotification]] = ???

  def nextApplicationReadyForAssessmentScoreEvaluation(currentPassmarkVersion: String): Future[Option[String]] = ???

  def saveAssessmentScoreEvaluation(applicationId: String, passmarkVersion: String, evaluationResult: AssessmentRuleCategoryResult,
    newApplicationStatus: ApplicationStatuses.EnumVal): Future[Unit] = ???

  def getSchemeLocations(applicationId: String): Future[List[String]] = ???

  def updateSchemeLocations(applicationId: String, locationIds: List[String]): Future[Unit] = ???

  def getSchemes(applicationId: String): Future[List[Scheme]] = ???

  def updateSchemes(applicationId: String, schemeNames: List[Scheme]): Future[Unit] = ???

  override def findAdjustments(applicationId: String): Future[Option[Adjustments]] = ???

  override def updateAdjustmentsComment(applicationId: String, adjustmentsComment: AdjustmentsComment): Future[Unit] = ???

  override def findAdjustmentsComment(applicationId: String): Future[AdjustmentsComment] = ???

  override def removeAdjustmentsComment(applicationId: String): Future[Unit] = ???

  override def findApplicationStatusDetails(applicationId: String): Future[ApplicationStatusDetails] = ???

  def findAssessmentCentreIndicator(appId: String): scala.concurrent.Future[Option[model.AssessmentCentreIndicator]] = ???

  def updateAssessmentCentreIndicator(applicationId: String, indicator: model.AssessmentCentreIndicator): scala.concurrent.Future[Unit] = ???

  override def find(applicationId: String): Future[Option[Candidate]] = ???

  def removeProgressStatuses(applicationId: String, progressStatuses: List[ProgressStatuses.ProgressStatus]): Future[Unit] = ???

  override def removeSchemes(applicationId: String): Future[Unit] = ???

  override def removeSchemeLocations(applicationId: String): Future[Unit] = ???
}
// scalastyle:on number.of.methods
