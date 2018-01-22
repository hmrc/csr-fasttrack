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

import connectors.PassMarkExchangeObjects
import connectors.PassMarkExchangeObjects.OnlineTestPassmarkSettings
import connectors.PassMarkExchangeObjects.Implicits._
import model.Commands._
import play.api.libs.json.{ JsNumber, JsObject }
import reactivemongo.api.DB
import reactivemongo.bson._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OnlineTestPassMarkSettingsRepository {
  def create(settings: OnlineTestPassmarkSettings, schemes: List[model.Scheme.Scheme]): Future[PassMarkSettingsCreateResponse]

  def tryGetLatestVersion(): Future[Option[OnlineTestPassmarkSettings]]
}

class PassMarkSettingsMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[OnlineTestPassmarkSettings, BSONObjectID](CollectionNames.PASS_MARK_SETTINGS, mongo,
      PassMarkExchangeObjects.Implicits.passMarkSettingsFormat, ReactiveMongoFormats.objectIdFormats) with OnlineTestPassMarkSettingsRepository {

  override def tryGetLatestVersion(): Future[Option[OnlineTestPassmarkSettings]] = {
    val query = BSONDocument()
    val sort = new JsObject(Map("createDate" -> JsNumber(-1)))
    collection.find(query).sort(sort).one[OnlineTestPassmarkSettings]
  }

  override def create(settings: OnlineTestPassmarkSettings, schemes: List[model.Scheme.Scheme]): Future[PassMarkSettingsCreateResponse] = {
    collection.insert(settings) flatMap { _ =>
      tryGetLatestVersion().map(createResponse =>
        PassMarkSettingsCreateResponse(createResponse.get.version, createResponse.get.createDate))
    }
  }
}
