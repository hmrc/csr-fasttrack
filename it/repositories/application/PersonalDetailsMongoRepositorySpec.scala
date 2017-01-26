package repositories.application

import factories.UUIDFactory
import model.persisted.PersonalDetails
import org.joda.time.LocalDate
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CollectionNames
import services.GBTimeZoneService
import testkit.MongoRepositorySpec

class PersonalDetailsMongoRepositorySpec extends MongoRepositorySpec with UUIDFactory {
  import ImplicitBSONHandlers._

  val collectionName = CollectionNames.APPLICATION
  def repository = new PersonalDetailsMongoRepository
  def generalApplicationRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  val userId = "userId"

  "Personal details repository" should {
    "find a candidate after he has been updated (not civil servant)" in {
      val appId = createApplication()
      val personalDetails = PersonalDetails("firstName", "lastName", "preferredName", new LocalDate("1990-11-25"),
        aLevel = false, stemLevel = false, civilServant = false, department = None)
      repository.update(appId, userId, personalDetails).futureValue

      val result = repository.find(appId).futureValue
      result mustBe personalDetails
    }

    "find a candidate after he has been updated (civil servant)" in {
      val appId = createApplication()
      val personalDetails = PersonalDetails("firstName", "lastName", "preferredName", new LocalDate("1990-11-25"),
        aLevel = false, stemLevel = false, civilServant = true, department = Some("department"))
      repository.update(appId, userId, personalDetails).futureValue

      val result = repository.find(appId).futureValue
      result mustBe personalDetails
    }
  }

  def createApplication(): String = {
    val appId = generateUUID()
    generalApplicationRepo.collection.insert(BSONDocument("applicationId" -> appId, "userId" -> userId)).futureValue
    appId
  }
}
