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

package repositories

object CollectionNames {

  val suffixForThisCampaign = "18test"

  val APPLICATION = s"application$suffixForThisCampaign"
  val APPLICATION_ASSESSMENT = s"application-assessment$suffixForThisCampaign"
  val APPLICATION_ASSESSMENT_SCORES = s"application-assessment-scores$suffixForThisCampaign"
  val ASSESSMENT_CENTRE_PASS_MARK_SETTINGS = s"assessment-centre-pass-mark-settings$suffixForThisCampaign"
  val CONTACT_DETAILS = s"contact-details$suffixForThisCampaign"
  val LOCKS = s"locks$suffixForThisCampaign"
  val MEDIA = s"media$suffixForThisCampaign"
  val ONLINE_TEST_PDF_REPORT = s"online-test-pdf-report$suffixForThisCampaign"
  val PASS_MARK_SETTINGS = s"pass-mark-settings$suffixForThisCampaign"
  val QUESTIONNAIRE = s"questionnaire$suffixForThisCampaign"
  val ONLINE_TEST_REPORT = s"online-test-report$suffixForThisCampaign"

  // Used by backup report
  val APPLICATION_PREFIX = "application"
  val APPLICATION_ASSESSMENT_PREFIX = "application-assessment"
  val APPLICATION_ASSESSMENT_SCORES_PREFIX = "application-assessment-scores"
  val CONTACT_DETAILS_PREFIX = "contact-details"
  val MEDIA_PREFIX = "media"
  val QUESTIONNAIRE_PREFIX = "questionnaire"
  val ONLINE_TEST_REPORT_PREFIX = "online-test-report"
}
