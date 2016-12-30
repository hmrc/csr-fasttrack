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

import play.Play
import play.api.libs.json.{ Json, Reads }
import resource._
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.collection.immutable.IndexedSeq
import scala.concurrent.Future

case class LocationSchemes(locationName: String, latitude: Double, longitude: Double, schemes: List[String])

protected case class Locations(locations: List[LocationSchemes])

case class SchemeInfo(schemeName: String, requiresALevel: Boolean, requiresALevelInStem: Boolean)

object FileLocationSchemeRepository extends LocationSchemeRepository

trait LocationSchemeRepository {

  implicit val locationReader: Reads[LocationSchemes] = (
    (__ \ "name").read[String] and
      (__ \ "lat").read[Double] and
      (__ \ "lng").read[Double] and
      (__ \ "schemes").read[List[String]]
    )(LocationSchemes.apply _)

  implicit val locationsReader: Reads[Locations] = Json.reads[Locations]

  private lazy val cachedLocationSchemes =  {
    val input = managed(Play.application.resourceAsStream("locations-schemes.json"))
    val loaded = input.acquireAndGet(r => Json.parse(r).as[Locations])
    Future.successful(loaded.locations.toIndexedSeq)
  }

  def getSchemesAndLocations: Future[IndexedSeq[LocationSchemes]] = cachedLocationSchemes

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
