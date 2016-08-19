package repositories

import model.PersistedObjects.PreferencesWithQualification
import model.Schemes._
import model.{ Alternatives, LocationPreference, Preferences }
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import testkit.MongoRepositorySpec

class FrameworkPreferenceRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "application"

  def frameworkPreferenceRepo = new FrameworkPreferenceMongoRepository()

  "Try get preferences with qualifications" should {
    val appId = "appId"
    "return preferences with qualifications" in {
      frameworkPreferenceRepo.collection.insert(BSONDocument(
        "applicationId" -> appId,
        "framework-preferences" -> BSONDocument(
          "firstLocation" -> BSONDocument(
            "region" -> "Region1",
            "location" -> "Location1",
            "firstFramework" -> "Commercial",
            "secondFramework" -> "Digital and technology"
          ),
          "secondLocation" -> BSONDocument(
            "region" -> "Region2",
            "location" -> "Location2",
            "firstFramework" -> "Business",
            "secondFramework" -> "Finance"
          ),
          "alternatives" -> BSONDocument(
            "location" -> true,
            "framework" -> true
          )
        ),
        "personal-details" -> BSONDocument(
          "aLevel" -> true,
          "stemLevel" -> false
        )
      )).futureValue

      val preferences = frameworkPreferenceRepo.tryGetPreferencesWithQualifications(appId).futureValue

      preferences mustBe Some(
        PreferencesWithQualification(Preferences(
          LocationPreference("Region1", "Location1", Commercial, Some(DigitalAndTechnology)),
          Some(LocationPreference("Region2", "Location2", Business, Some(Finance))),
          None,
          Some(Alternatives(location = true, framework = true))
        ), aLevel = true, stemLevel = false)
      )
    }

    "return none when there is no framework-preferences" in {
      frameworkPreferenceRepo.collection.insert(BSONDocument(
        "applicationId" -> appId,
        "personal-details" -> BSONDocument(
          "aLevel" -> true,
          "stemLevel" -> false
        )
      )).futureValue

      val preferences = frameworkPreferenceRepo.tryGetPreferencesWithQualifications(appId).futureValue

      preferences mustBe None
    }

    "return none when there is no personal-details" in {
      frameworkPreferenceRepo.collection.insert(BSONDocument(
        "applicationId" -> appId,
        "framework-preferences" -> BSONDocument(
          "firstLocation" -> BSONDocument(
            "region" -> "Region1",
            "location" -> "Location1",
            "firstFramework" -> "Commercial",
            "secondFramework" -> "Digital and technology"
          )
        )
      )).futureValue

      val preferences = frameworkPreferenceRepo.tryGetPreferencesWithQualifications(appId).futureValue

      preferences mustBe None
    }
  }
}