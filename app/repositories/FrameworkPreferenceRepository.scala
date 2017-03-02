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

import model.Exceptions.SchemePreferencesNotFound
import model.PersistedObjects.PreferencesWithQualification
import model.Scheme._
import model.{ ApplicationStatuses, Preferences }
import reactivemongo.api.DB
import reactivemongo.bson.{ BSONDocument, BSONObjectID, _ }
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FrameworkPreferenceRepository {
  def savePreferences(applicationId: String, preferences: Preferences): Future[Unit]

  def tryGetPreferences(applicationId: String): Future[Option[Preferences]]

  def tryGetPreferencesWithQualifications(applicationId: String): Future[Option[PreferencesWithQualification]]
}

class FrameworkPreferenceMongoRepository(implicit mongo: () => DB)
    extends ReactiveRepository[Preferences, BSONObjectID](
      CollectionNames.APPLICATION, mongo, Preferences.jsonFormat, ReactiveMongoFormats.objectIdFormats
    ) with FrameworkPreferenceRepository {

  def savePreferences(applicationId: String, preferences: Preferences): Future[Unit] = {
    require(preferences.isValid, "Preferences must be valid when saving to repository")

    val query = BSONDocument("applicationId" -> applicationId)
    val preferencesBSON = BSONDocument("$set" -> BSONDocument(
      "applicationStatus" -> ApplicationStatuses.InProgress,
      "progress-status.frameworks-location" -> preferences.alternatives.isDefined,
      "framework-preferences" -> preferences
    ))
    collection.update(query, preferencesBSON, upsert = false) map {
      case _ => ()
    }
  }

  def tryGetPreferences(applicationId: String): Future[Option[Preferences]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("framework-preferences" -> 1, "_id" -> 0)
    collection.find(query, projection).one[BSONDocument].map { rootDocument =>
      rootDocument.flatMap(_.getAs[Preferences]("framework-preferences"))
    }
  }

  def tryGetPreferencesWithQualifications(applicationId: String): Future[Option[PreferencesWithQualification]] = {
    val query = BSONDocument("applicationId" -> applicationId)
    val projection = BSONDocument("schemes" -> 1, "personal-details" -> 1, "_id" -> 0)

    collection.find(query, projection).one[BSONDocument].map {
      case Some(document) if document.getAs[BSONDocument]("personal-details").isDefined =>
        val root = document.getAs[BSONDocument]("personal-details").get
        val aLevel = root.getAs[Boolean]("aLevel").get
        val stemLevel = root.getAs[Boolean]("stemLevel").get

        val schemes = if (document.getAs[List[Scheme]]("schemes").isDefined) {
          document.getAs[List[Scheme]]("schemes").get
        } else {
          throw SchemePreferencesNotFound(applicationId)
        }

        Some(PreferencesWithQualification(schemes, aLevel, stemLevel))
      case _ => None
    }
  }
}
