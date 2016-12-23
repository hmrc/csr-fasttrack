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

package repositories

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future

case class LocationSchemes(locationName: String, latitude: Double, longitude: Double, schemes: IndexedSeq[String])
case class SchemeInfo(schemeName: String, requiresALevel: Boolean, requiresALevelInStem: Boolean)

trait LocationSchemeRepository {
  def getSchemesAndLocations: Future[IndexedSeq[LocationSchemes]] = {
    Future.successful(
      IndexedSeq(LocationSchemes("Airdrie", 55.86602, 3.98025, IndexedSeq("Business", "Commercial")))
    )
  }
  def getSchemeInfo: Future[IndexedSeq[SchemeInfo]] = {
    Future.successful(IndexedSeq(
      SchemeInfo("Business", requiresALevel = true, requiresALevelInStem = true),
      SchemeInfo("Commercial", requiresALevel = true, requiresALevelInStem = true),
      SchemeInfo("Digital and technology", requiresALevel = true, requiresALevelInStem = true),
      SchemeInfo("Finance", requiresALevel = true, requiresALevelInStem = true),
      SchemeInfo("Project Delivery", requiresALevel = true, requiresALevelInStem = true)
    ))
  }
}
