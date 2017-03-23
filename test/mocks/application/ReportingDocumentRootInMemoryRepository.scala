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

import model.Commands._
import model.ReportExchangeObjects._
import model._
import model.exchange.AssistanceDetails
import model.report.AdjustmentReportItem
import org.joda.time.LocalDate
import repositories.application.ReportingRepository

import scala.collection.mutable
import scala.concurrent.Future
object ReportingDocumentRootInMemoryRepository extends ReportingDocumentRootInMemoryRepository

/**
 * @deprecated Please use Mockito
 */
class ReportingDocumentRootInMemoryRepository extends ReportingRepository {

  val inMemoryRepo = new mutable.HashMap[String, ApplicationResponse]

  override def applicationsReport(frameworkId: String): Future[List[(String, IsNonSubmitted, PreferencesWithContactDetails)]] = {
    val app1 = ("user1", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      Some("user1@email.com"), None, Some("location1"), Some("location1Scheme1"), Some("location1Scheme2"), Some("location2"),
      Some("location2Scheme1"), Some("location2Scheme2"), None, Some("2016-12-25 13:00:14")))
    val app2 = ("user2", true, PreferencesWithContactDetails(Some("John"), Some("Smith"), Some("Jo"),
      None, None, None, None, None, None, None, None, None, None))
    Future.successful(app1 :: app2 :: Nil)
  }

  override def applicationsForCandidateProgressReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]] = Future.successful(List(
    ApplicationForCandidateProgressReport(Some(UniqueIdentifier.randomUniqueIdentifier), UniqueIdentifier.randomUniqueIdentifier,
      Some("SUBMITTED"), List.empty, List.empty, None, None, None, None, None, None, None),
    ApplicationForCandidateProgressReport(Some(UniqueIdentifier.randomUniqueIdentifier), UniqueIdentifier.randomUniqueIdentifier,
      Some("IN_PROGRESS"), List.empty, List.empty, None, None, None, None, None, None, None),
    ApplicationForCandidateProgressReport(Some(UniqueIdentifier.randomUniqueIdentifier), UniqueIdentifier.randomUniqueIdentifier,
      Some("SUBMITTED"), List.empty, List.empty, None, None, None, None, None, None, None)
  ))

  override def adjustmentReport(frameworkId: String): Future[List[AdjustmentReportItem]] =
    Future.successful(
      List(
        AdjustmentReportItem("1", Some("123"), Some("John"), Some("Smith"), Some("Spiderman"),
          None, None, Some("SUBMITTED"), Some(AssistanceDetails("Yes", None, Some(true), true, Some("more time"), true, Some("big chair"))),
          Some(Adjustments(None, None, None, None)), Some(AdjustmentsComment("comment")), Some(AssessmentCentreIndicator("Sutton", "London"))),
        AdjustmentReportItem("2", Some("123"), Some("Mary"), Some("Smith"), Some("Spiderwoman"),
          None, None, Some("SUBMITTED"), Some(AssistanceDetails("Yes", None, Some(true), true, Some("more time"), true, Some("big chair"))),
          Some(Adjustments(None, None, None, None)), Some(AdjustmentsComment("comment")), Some(AssessmentCentreIndicator("Sutton", "London"))),
        AdjustmentReportItem("3", Some("123"), Some("Peter"), Some("Smith"), Some("Spiderchild"),
          None, None, Some("SUBMITTED"), Some(AssistanceDetails("Yes", None, Some(true), true, Some("more time"), true, Some("big chair"))),
          Some(Adjustments(None, None, None, None)), Some(AdjustmentsComment("comment")), Some(AssessmentCentreIndicator("Sutton", "London")))
      )
    )

  override def candidatesAwaitingAllocation(frameworkId: String): Future[List[CandidateAwaitingAllocation]] =
    Future.successful(
      List(
        CandidateAwaitingAllocation("1", "John", "Smith", "Spiderman", new LocalDate(1988, 1, 21), Some("Some adjustments"), "London"),
        CandidateAwaitingAllocation("2", "James", "Jones", "Batman", new LocalDate(1992, 11, 30), Some("Some adjustments"), "Bournemouth"),
        CandidateAwaitingAllocation("3", "Katherine", "Jones", "Supergirl", new LocalDate(1990, 2, 12), None, "Queer Camel")
      )
    )

  override def candidateProgressReportNotWithdrawnWithPersonalDetails(frameworkId: String): Future[List[ReportWithPersonalDetails]] = ???

  override def allApplicationAndUserIds(frameworkId: String): Future[List[ApplicationUserIdReport]] = ???

  override def applicationsPassedInAssessmentCentre(frameworkId: String): Future[List[ApplicationPreferencesWithTestResults]] = ???

  override def diversityReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]] = ???

  override def passMarkReport(frameworkId: String): Future[List[ApplicationForCandidateProgressReport]] = ???

  override def assessmentCentreIndicatorReport: Future[List[AssessmentCentreIndicatorReport]] = ???
}
