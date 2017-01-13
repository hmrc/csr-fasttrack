package repositories.assistancedetails

import model.Exceptions.AssistanceDetailsNotFound
import model.exchange.AssistanceDetailsExamples
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.application.AssistanceDetailsMongoRepository
import testkit.MongoRepositorySpec

class AssistanceDetailsRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  override val collectionName = "application"

  def repository = new AssistanceDetailsMongoRepository

  "update and find" should {

    "create new assistance details if they do not exist" in {
      val result = (for {
        _ <- insert(minimumApplicationBSON(applicationId(1), userId(1)))
        _ <- repository.update(applicationId(1), userId(1), AssistanceDetailsExamples.DisabilityGisAndAdjustments)
        ad <- repository.find(applicationId(1))
      } yield ad).futureValue

      result must be(model.persisted.AssistanceDetails.fromExchange(AssistanceDetailsExamples.DisabilityGisAndAdjustments))
    }

    "update assistance details when they exist and find them successfully" in {
      val result = (for {
        _ <- insert(applicationBSONWithFullAssistanceDetails(applicationId(3), userId(3)))
        _ <- repository.update(applicationId(3), userId(3), AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments )
        ad <- repository.find(applicationId(3))
      } yield ad).futureValue

      result must be(model.persisted.AssistanceDetails.fromExchange(AssistanceDetailsExamples.OnlyDisabilityNoGisNoAdjustments))
    }
  }

  "find" should {
    "return an exception when application does not exist" in {
      val result = repository.find(applicationId(4)).failed.futureValue
      result mustBe AssistanceDetailsNotFound(applicationId(4))
    }
  }

  private def insert(doc: BSONDocument) = repository.collection.insert(doc)

  private def userId(i: Int) = "UserId" + i
  private def applicationId(i: Int) = "AppId" + i

  private def minimumApplicationBSON(applicationId: String, userId: String) = BSONDocument(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId
  )

  private def applicationBSONWithFullAssistanceDetails(applicationId: String, userId: String) = BSONDocument(
    "applicationId" -> applicationId,
    "userId" -> userId,
    "frameworkId" -> FrameworkId,
    "assistance-details" -> BSONDocument(
      "hasDisability" -> "Yes",
      "hasDisabilityDescription" -> "My disability",
      "guaranteedInterview" -> true,
      "needsSupportForOnlineAssessment" -> true,
      "needsSupportForOnlineAssessmentDescription" -> "needsSupportForOnlineAssessmentDescription",
      "needsSupportAtVenue" -> true,
      "needsSupportAtVenueDescription" -> "needsSupportAtVenueDescription"
    )
  )
}
