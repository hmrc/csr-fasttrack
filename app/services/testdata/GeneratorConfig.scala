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

package services.testdata

import model.EvaluationResults.Result
import services.testdata.faker.DataFaker.Random

case class GeneratorConfig(emailPrefix: String, setGis: Boolean = false, cubiksUrl: String, region: Option[String],
                           loc1scheme1Passmark: Option[Result], loc1scheme2Passmark: Option[Result],
                           previousStatus: Option[String], confirmedAllocation: Boolean = true)

object GeneratorConfig {
  def apply(cubiksUrlFromConfig: String, o: model.exchange.testdata.CreateCandidateInStatusRequest)(generatorId: Int): GeneratorConfig = {

    val statusData = StatusData(o.statusData)

    GeneratorConfig(
      emailPrefix = s"tesf${Random.number()-1}",
      previousStatus = statusData.previousApplicationStatus,
      cubiksUrl = cubiksUrlFromConfig,
      region = o.region,
      loc1scheme1Passmark = o.loc1scheme1EvaluationResult.map(Result.apply),
      loc1scheme2Passmark = o.loc1scheme2EvaluationResult.map(Result.apply)
    )
  }
}

case class StatusData(applicationStatus: String,
                       previousApplicationStatus: Option[String] = None,
                       progressStatus: Option[String] = None)

object StatusData {
  def apply(o: model.exchange.testdata.StatusDataRequest): StatusData = {
    StatusData(applicationStatus = o.applicationStatus,
      previousApplicationStatus = o.previousApplicationStatus,
      progressStatus = o.progressStatus
    )
  }
}