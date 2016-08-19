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

package model

object Schemes {
  val Business = "Business"
  val Commercial = "Commercial"
  val DigitalAndTechnology = "Digital and technology"
  val Finance = "Finance"
  val ProjectDelivery = "Project delivery"

  case class Requirement(aLevel: Boolean, stemLevel: Boolean)

  val AllSchemes = Business :: Commercial :: DigitalAndTechnology :: Finance :: ProjectDelivery :: Nil

  val AllSchemeRequirements = Map(
    Business -> Requirement(aLevel = false, stemLevel = false),
    Commercial -> Requirement(aLevel = false, stemLevel = false),
    Finance -> Requirement(aLevel = false, stemLevel = false),
    ProjectDelivery -> Requirement(aLevel = true, stemLevel = false),
    DigitalAndTechnology -> Requirement(aLevel = false, stemLevel = true)
  )

}
