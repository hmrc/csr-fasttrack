package repositories

import model.AssessmentCentreIndicator
import testkit.{ ShortTimeout, UnitWithAppSpec }

class AssessmentCentreIndicatorCSVRepositorySpec extends UnitWithAppSpec with ShortTimeout {

  "North South Indicator Repository" should {
    "parse file with expected number of post code areas" in {
      val result = AssessmentCentreIndicatorCSVRepository.assessmentCentreIndicators
      result.size mustBe 121
    }
  }

  "calculateFsacIndicator" should {
    "return default indicator when no postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(None)
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "return default indicator when no postcode match is found" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("BOGUS3"))
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "return default indicator for an empty postcode " in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some(""))
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "ignore postcode if outside UK and return the default indicator" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("OX1 4DB"))
      result mustBe Some(AssessmentCentreIndicatorCSVRepository.DefaultIndicator)
    }
    "return London for Oxford postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("OX1 4DB"))
      result mustBe Some(AssessmentCentreIndicator("Oxford", "London"))
    }
    "return Newcastle for Edinburgh postcode" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("EH1 3EG"))
      result mustBe Some(AssessmentCentreIndicator("Edinburgh", "Newcastle"))
    }
    "return London even when postcode is lowercase" in {
      val result = AssessmentCentreIndicatorCSVRepository.calculateIndicator(Some("ec1v 3eg"))
      result mustBe Some(AssessmentCentreIndicator("East Central London", "London"))
    }
  }

}
