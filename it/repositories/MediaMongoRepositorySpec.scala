package repositories

import model.Commands.AddMedia
import model.UniqueIdentifier
import testkit.MongoRepositorySpec

class MediaMongoRepositorySpec extends MongoRepositorySpec {
  override val collectionName = CollectionNames.MEDIA

  def createUUID = UniqueIdentifier.randomUniqueIdentifier

  def mediaRepo = new MediaMongoRepository

  "Media find all" should {
    "return nothing if the collection is empty" in {
      val result = mediaRepo.findAll().futureValue
      result mustBe empty
    }

    "return a list of media" in {
      val user1 = createUUID
      val user2 = createUUID
      val media1 = AddMedia(user1.toString(), "media1")
      val media2 = AddMedia(user2.toString(), "media2")
      mediaRepo.create(media1).futureValue
      mediaRepo.create(media2).futureValue

      val result = mediaRepo.findAll().futureValue

      result must contain theSameElementsAs Map(
        user1 -> "media1",
        user2 -> "media2"
      )
    }
  }
}
