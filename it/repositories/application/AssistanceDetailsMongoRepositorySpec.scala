package repositories.application

import model.ApplicationStatuses
import model.Exceptions.ApplicationNotFound
import model.exchange.AssistanceDetails
import model.persisted.ApplicationAssistanceDetails
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers
import repositories.CollectionNames
import testkit.MongoRepositorySpec

class AssistanceDetailsMongoRepositorySpec extends MongoRepositorySpec {
  import ImplicitBSONHandlers._

  val collectionName: String = CollectionNames.APPLICATION

  def repository = new AssistanceDetailsMongoRepository

  "find application" should {
    val cubiksUserId = 1
    val appAssistanceDetails = ApplicationAssistanceDetails(
      applicationId = "app1",
      applicationStatus = ApplicationStatuses.OnlineTestCompleted,
      assistanceDetails = AssistanceDetails(
        hasDisability = "No",
        hasDisabilityDescription = None,
        guaranteedInterview = None,
        needsSupportForOnlineAssessment = true,
        needsSupportForOnlineAssessmentDescription = None,
        needsSupportAtVenue = true,
        needsSupportAtVenueDescription = None
      )
    )

    "throw an Application Not Found if there is no application" in {
      val ex = repository.findApplication(cubiksUserId).failed.futureValue
      ex mustBe an[ApplicationNotFound]
    }

    "throw an Illegal State Exception if there is no Assistance Details" in {
      createWithoutAssistanceDetails(1, "appId")
      val ex = repository.findApplication(cubiksUserId).failed.futureValue
      ex mustBe an[IllegalStateException]
    }

    "convert application to gis" in {
      create(cubiksUserId = 1, application = appAssistanceDetails)
      repository.updateToGis(appAssistanceDetails.applicationId).futureValue
      val result = repository.findApplication(cubiksUserId).futureValue
      result.assistanceDetails.guaranteedInterview mustBe Some(true)
      result.assistanceDetails.hasDisability mustBe "Yes"
      result.assistanceDetails.needsSupportForOnlineAssessment mustBe false
    }

    "return an application" in {
      create(cubiksUserId = 1, application = appAssistanceDetails)
      val result = repository.findApplication(cubiksUserId).futureValue
      result mustBe appAssistanceDetails
    }
  }

  private def create(cubiksUserId: Int, application: ApplicationAssistanceDetails) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> application.applicationId,
      "applicationStatus" -> application.applicationStatus,
      "assistance-details" -> application.assistanceDetails,
      "online-tests" -> BSONDocument(
        "cubiksUserId" -> cubiksUserId
      )
    )).futureValue
  }

  private def createWithoutAssistanceDetails(cubiksUserId: Int, appId: String) = {
    repository.collection.insert(BSONDocument(
      "applicationId" -> appId,
      "applicationStatus" -> ApplicationStatuses.OnlineTestCompleted,
      "online-tests" -> BSONDocument(
        "cubiksUserId" -> cubiksUserId
      )
    )).futureValue
  }
}
