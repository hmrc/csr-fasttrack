package services.adjustments

import connectors.EmailClient
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import model.{ AdjustmentDetail, Adjustments }
import repositories.ContactDetailsRepository
import repositories.application.GeneralApplicationRepository
import services.adjustmentsmanagement.AdjustmentsManagementService
import services.{ AuditService, BaseServiceSpec }


class AdjustmentManagementServiceSpec extends BaseServiceSpec {

  "confirm adjustments" should {
    "confirm the candidate adjustments" in new TestFixture {
      confirmAdjustment(appId, onlineTestsAdjustments, actionTriggeredBy)
    }
  }





  trait TestFixture extends AdjustmentsManagementService {
    val appRepository = mock[GeneralApplicationRepository]
    val cdRepository = mock[ContactDetailsRepository]
    val emailClient = mock[EmailClient]
    val auditService = mock[AuditService]

    val appId = "appId"
    val actionTriggeredBy = "adminId"

    val onlineTestsAdjustments = Adjustments(typeOfAdjustments = Some(List("timeExtension")),
      onlineTests = Some(AdjustmentDetail(extraTimeNeeded = Some(9), extraTimeNeededNumerical = Some(12))))

    when(appRepository.find(eqTo(appId)))
  }
}


