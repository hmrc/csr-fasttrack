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

package repositories.application

import factories.UUIDFactory
import model.ReportExchangeObjects.ApplicationForCandidateProgressReport
import model._
import reactivemongo.json.ImplicitBSONHandlers
import repositories.{ CollectionNames }
import services.GBTimeZoneService
import services.testdata.{ TestDataGeneratorService }
import testkit.MongoRepositorySpec

import scala.language.postfixOps

class ReportingMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {

  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION

  def repository = new ReportingMongoRepository(GBTimeZoneService)

    def testDataRepo = new TestDataMongoRepository()

  val testDataGeneratorService = TestDataGeneratorService


  "Applications for Candidate Progress Report" must {
    "return a report with one application when there is only one application with the corresponding fields when" +
      "all fields are populated" in {
      val userId = UniqueIdentifier.randomUniqueIdentifier
      val appId = UniqueIdentifier.randomUniqueIdentifier

      testDataRepo.createApplicationWithAllFields(userId.toString(), appId.toString(), "FastTrack-2015").futureValue

      val result = repository.applicationsForCandidateProgressReport("FastTrack-2015").futureValue

      result must not be empty
      result.head mustBe ApplicationForCandidateProgressReport(Some(appId), userId, Some("assistance_details_completed"),
        List(Scheme.Commercial, Scheme.Business), List("2643743", "2657613"), Some("Yes"), Some(false), Some(true),
        Some(true), Some(Adjustments(typeOfAdjustments=Some(List("onlineTestsTimeExtension", "onlineTestsOther",
          "assessmentCenterTimeExtension", "coloured paper", "braille test paper", "room alone", "rest breaks",
          "reader/assistant", "stand up and move around", "assessmentCenterOther")), adjustmentsConfirmed = Some(true),
          onlineTests = Some(AdjustmentDetail(extraTimeNeeded=Some(25), extraTimeNeededNumerical=Some(60), otherInfo=Some("other adjustments"))),
          assessmentCenter = Some(AdjustmentDetail(extraTimeNeeded=Some(30), extraTimeNeededNumerical=None,
            otherInfo=Some("Other assessment centre adjustment")))
        )), Some(false), None)
    }
  }
}
