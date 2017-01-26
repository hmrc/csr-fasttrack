package services.adjustments

import connectors.EmailClient
import model.Commands.{ Address, Candidate }
import model.PersistedObjects.ContactDetails
import org.mockito.Matchers.{ eq => eqTo, _ }
import org.mockito.Mockito._
import model.{ AdjustmentDetail, Adjustments }
import repositories.ContactDetailsRepository
import repositories.application.GeneralApplicationRepository
import services.adjustmentsmanagement.AdjustmentsManagementService
import services.{ AuditService, BaseServiceSpec }
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


class AdjustmentManagementServiceSpec extends BaseServiceSpec {

  "confirm adjustments" should {
    "send adjustments confirmation email" in new TestFixture {
      val auditEvent = s"Candidate $userId AdjustmentsConfirmed by $actionTriggeredBy"
      when(appRepository.confirmAdjustments(eqTo(appId), eqTo(onlineTestsAdjustments))).thenReturn(emptyFuture)
      confirmAdjustment(appId, onlineTestsAdjustments, actionTriggeredBy).futureValue
      verify(emailClient).sendAdjustmentsConfirmation(eqTo(email), eqTo(preferredName), any[String], any[String])(any[HeaderCarrier])
      verify(auditService).logEvent(eqTo(auditEvent))(eqTo(hc), eqTo(rh))
    }

    "send adjustments changed email" in new TestFixture {
      val auditEvent = s"Candidate $userId AdjustmentsUpdated by $actionTriggeredBy"
      when(appRepository.findAdjustments(eqTo(appId))
      ).thenReturn(Future.successful(Some(onlineTestsAdjustments.copy(adjustmentsConfirmed = Some(true)))))
      when(appRepository.confirmAdjustments(eqTo(appId), eqTo(onlineTestsAdjustments))).thenReturn(emptyFuture)
      confirmAdjustment(appId, onlineTestsAdjustments, actionTriggeredBy).futureValue
      verify(emailClient).sendAdjustmentsUpdateConfirmation(eqTo(email), eqTo(preferredName), any[String], any[String])(any[HeaderCarrier])
      verify(auditService).logEvent(eqTo(auditEvent))(eqTo(hc), eqTo(rh))
    }

    "create adjustments removed audit service" in new TestFixture {
      val auditEvent = s"Candidate $userId AdjustmentsConfirmed by $actionTriggeredBy"
      val adjustments = onlineTestsAdjustments.copy(typeOfAdjustments = None)
      when(appRepository.confirmAdjustments(eqTo(appId), eqTo(adjustments))).thenReturn(emptyFuture)
      confirmAdjustment(appId, adjustments, actionTriggeredBy).futureValue
      verify(emailClient).sendAdjustmentsConfirmation(eqTo(email), eqTo(preferredName), any[String], any[String])(any[HeaderCarrier])
      verify(auditService).logEvent(eqTo(auditEvent))(eqTo(hc), eqTo(rh))
    }
  }

  trait TestFixture extends AdjustmentsManagementService {
    val appRepository = mock[GeneralApplicationRepository]
    val cdRepository = mock[ContactDetailsRepository]
    val emailClient = mock[EmailClient]
    val auditService = mock[AuditService]

    val userId = "userId"
    val appId = "appId"
    val email = "email@loc.com"
    val firstName = "Joe"
    val lastName = "Blogs"
    val preferredName = "JoeBlogs"
    val actionTriggeredBy = "adminId"

    val candidate = Candidate(userId, Some(appId), Some(email), Some(firstName), Some(lastName), Some(preferredName), None, None, None)
    val contactDetails = ContactDetails(Address("line1"), "TW11ER", email, None)

    val onlineTestsAdjustments = Adjustments(typeOfAdjustments = Some(List("timeExtension")),
      onlineTests = Some(AdjustmentDetail(extraTimeNeeded = Some(9), extraTimeNeededNumerical = Some(12))))

    when(appRepository.find(eqTo(appId))).thenReturn(Future.successful(Some(candidate)))
    when(appRepository.findAdjustments(eqTo(appId))).thenReturn(Future.successful(None))
    doNothing().when(auditService).logEvent(any[String])(eqTo(hc), eqTo(rh))
    when(emailClient.sendAdjustmentsConfirmation(eqTo(email), eqTo(preferredName), any[String], any[String])(
      eqTo(hc))).thenReturn(emptyFuture)
    when(emailClient.sendAdjustmentsUpdateConfirmation(eqTo(email), eqTo(preferredName), any[String], any[String])(
      eqTo(hc))).thenReturn(emptyFuture)
    when(cdRepository.find(eqTo(userId))).thenReturn(Future.successful(contactDetails))

  }
}


