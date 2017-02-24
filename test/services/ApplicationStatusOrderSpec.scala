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

package services

import model.{ ApplicationStatusOrder, ProgressStatuses }
import model.Commands.{ ProgressResponse, QuestionnaireProgressResponse }
import model.commands.OnlineTestProgressResponse
import org.scalatestplus.play.PlaySpec

class ApplicationStatusOrderSpec extends PlaySpec {

  import ApplicationStatusOrderSpec._

  "no progress status" should {
    "return registered" in {
      ApplicationStatusOrder.getStatus(None) must be(ProgressStatuses.RegisteredProgress)
    }
  }

  "a registered application" should {
    "return registered" in {
      val status = ApplicationStatusOrder.getStatus(ProgressResponse("id"))
      status must be(ProgressStatuses.RegisteredProgress)
    }
  }

  "a withdrawn application" should {
    "return withdrawn" in {
      ApplicationStatusOrder.getStatus(progress) must be(ProgressStatuses.WithdrawnProgress)
    }
    "return withdrawn when all other progresses are set" in {
      ApplicationStatusOrder.getStatus(completeProgress) must be(ProgressStatuses.WithdrawnProgress)
    }
  }

  "a submitted application" should {
    "return submitted" in {
      val customProgress = progress.copy(withdrawn = false)
      ApplicationStatusOrder.getStatus(customProgress) must be(ProgressStatuses.SubmittedProgress)
    }
  }

  "a reviewed application" should {
    "return reviewed" in {
      val customProgress = progress.copy(withdrawn = false, submitted = false,
        questionnaire = QuestionnaireProgressResponse())
      ApplicationStatusOrder.getStatus(customProgress) must be(ProgressStatuses.ReviewCompletedProgress)
    }
  }

  "an application in framework and locations" should {
    "return schemes_completed" in {
      val customProgress = emptyProgress.copy(personalDetails = true, hasSchemeLocations = true, hasSchemes = true)
      ApplicationStatusOrder.getStatus(customProgress) must be(ProgressStatuses.SchemesPreferencesCompletedProgress)
    }
  }

  "an application in personal details" should {
    "return personal_details_completed" in {
      val customProgress = emptyProgress.copy(personalDetails = true)
      ApplicationStatusOrder.getStatus(customProgress) must be(ProgressStatuses.PersonalDetailsCompletedProgress)
    }
    "return personal_details_completed when sections are not completed" in {
      val customProgress = emptyProgress.copy(personalDetails = true, hasSchemeLocations = false, hasSchemes = false)
      ApplicationStatusOrder.getStatus(customProgress) must be(ProgressStatuses.PersonalDetailsCompletedProgress)
    }
  }

  "non-submitted status" should {
    import ApplicationStatusOrder._

    "be true for non submitted progress" in {
      isNonSubmittedStatus(emptyProgress.copy(submitted = false, withdrawn = false)) must be(true)
    }

    "be false for withdrawn progress" in {
      isNonSubmittedStatus(emptyProgress.copy(submitted = true, withdrawn = true)) must be(false)
      isNonSubmittedStatus(emptyProgress.copy(submitted = false, withdrawn = true)) must be(false)
    }

    "be false for submitted but not withdrawn progress" in {
      isNonSubmittedStatus(emptyProgress.copy(submitted = true, withdrawn = false)) must be(false)
    }
  }
}

object ApplicationStatusOrderSpec {

  val progress = ProgressResponse("1", personalDetails = true, hasSchemeLocations = true, hasSchemes = true,
    assistanceDetails = true, review = true, QuestionnaireProgressResponse(
    diversityStarted = true,
    diversityCompleted = true, educationCompleted = true, occupationCompleted = true
  ), submitted = true, withdrawn = true)

  val emptyProgress = ProgressResponse("1")

  val completeProgress = ProgressResponse("1", personalDetails = true, hasSchemeLocations = true, hasSchemes = true, assistanceDetails = true,
    review = true,
    QuestionnaireProgressResponse(diversityStarted = true, diversityCompleted = true, educationCompleted = true, occupationCompleted = true),
    submitted = true, withdrawn = true,
    OnlineTestProgressResponse(invited = true, started = true, completed = true, allocationConfirmed = true))
}
