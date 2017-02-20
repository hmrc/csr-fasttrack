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
  import PersonalDetailsMongoRepositoryFixture._

  val collectionName = CollectionNames.APPLICATION
  def repository = new PersonalDetailsMongoRepository
  def generalApplicationRepo = new GeneralApplicationMongoRepository(GBTimeZoneService)
  val userId = "userId"

  "Personal details repository" should {
    "find a candidate after he has been updated (not civil servant)" in {
      val appId = createApplication()
      repository.update(appId, userId, ExpectedPersonalDetails).futureValue

      val result = repository.find(appId).futureValue
      result mustBe ExpectedPersonalDetails
    }

    "find a candidate after he has been updated (civil servant)" in {
      val appId = createApplication()

      repository.update(appId, userId, ExpectedPersonalDetails).futureValue

      val result = repository.find(appId).futureValue
      result mustBe ExpectedPersonalDetails
    }

    "update personal details and status" in {
      val appId = createApplicationInStatus("AWAITING_ALLOCATION")
      repository.update(appId, userId, ExpectedPersonalDetails).futureValue
      val statusDetails = generalApplicationRepo.findApplicationStatusDetails(appId).futureValue
      val personalDetails = repository.find(appId).futureValue

      statusDetails.status.name mustBe "IN_PROGRESS"
      personalDetails mustBe ExpectedPersonalDetails
    }

    "update only personal details" in {
      val appId = createApplicationInStatus("AWAITING_ALLOCATION")
      repository.u(appId, userId, ExpectedPersonalDetails).futureValue
      val statusDetails = generalApplicationRepo.findApplicationStatusDetails(appId).futureValue
      val personalDetails = repository.find(appId).futureValue

      statusDetails.status.name mustBe "IN_PROGRESS"
      personalDetails mustBe ExpectedPersonalDetails


    }
  }

  def createApplication(): String = {
    val appId = generateUUID()
    generalApplicationRepo.collection.insert(BSONDocument("applicationId" -> appId, "userId" -> userId)).futureValue
    appId
  }

  def createApplicationInStatus(status: String): String = {
    val appId = generateUUID()
    generalApplicationRepo.collection.insert(BSONDocument("applicationId" -> appId,
      "userId" -> userId,
      "applicationStatus" -> status)).futureValue
    appId
  }
}

object PersonalDetailsMongoRepositoryFixture {

  val ExpectedPersonalDetails = PersonalDetails("firstName", "lastName", "preferredName", new LocalDate("1990-11-25"),
    aLevel = false, stemLevel = false, civilServant = true, department = Some("department"))



}
