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

package repositories

import java.util.UUID

import connectors.PassMarkExchangeObjects
import connectors.PassMarkExchangeObjects.{ Scheme, SchemeThreshold, SchemeThresholds, Settings }
import connectors.PassMarkExchangeObjects.Implicits._
import model.Commands._
import org.joda.time.DateTime
import play.api.libs.json.{ JsNumber, JsObject }
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PassMarkSettingsRepository {
  def create(settings: Settings, schemeIds: List[model.Scheme.Scheme]): Future[PassMarkSettingsCreateResponse]

  def tryGetLatestVersion(schemeNames: List[model.Scheme.Scheme]): Future[Option[Settings]]
}

class PassMarkSettingsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Settings, BSONObjectID](CollectionNames.PASS_MARK_SETTINGS, mongo,
    PassMarkExchangeObjects.Implicits.passMarkSettingsFormat, ReactiveMongoFormats.objectIdFormats) with PassMarkSettingsRepository {

  override def create(settings: Settings, schemeIds: List[model.Scheme.Scheme]): Future[PassMarkSettingsCreateResponse] = {
    collection.insert(settings) flatMap { _ =>
      tryGetLatestVersion(schemeIds).map(createResponse =>
        PassMarkSettingsCreateResponse(createResponse.get.version, createResponse.get.createDate))
    }
  }

  override def tryGetLatestVersion(schemeNames: List[model.Scheme.Scheme]): Future[Option[Settings]] = {
    val query = BSONDocument()
    val sort = new JsObject(Map("createDate" -> JsNumber(-1)))

    collection.find(query).sort(sort).one[Settings]
  }
}
