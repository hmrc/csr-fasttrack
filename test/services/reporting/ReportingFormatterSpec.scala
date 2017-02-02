package services.reporting

import model.{ AdjustmentDetail, Adjustments }
import services.BaseServiceSpec

class ReportingFormatterSpec extends BaseServiceSpec {
  "getOnlineAdjustments" should {
    "return Some(No) when needs online adjustments is false" in {
      ReportingFormatter.getOnlineAdjustments(Some(false), None) mustBe (Some("No"))
    }

    "return None when needs online adjustments is None" in {
      ReportingFormatter.getOnlineAdjustments(None, None) mustBe (None)
    }

    "return Some(Yes) when needs online adjustments is true and adjustments have not been saved" in {
      ReportingFormatter.getOnlineAdjustments(Some(true), None) mustBe (Some("Yes"))
    }

    "return Verbal extra time needed string when needs online adjustments and extra time for verbal online test have been requested" in {
      val result = ReportingFormatter.getOnlineAdjustments(
        onlineAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = None, adjustmentsConfirmed = Some(true),
          onlineTests = Some(AdjustmentDetail(extraTimeNeeded = Some(30))))))
      result mustBe (Some("Verbal extra time needed: 30%"))
    }

    "return Numerical extra time needed string when needs online adjustments and extra time for numerical online test have been requested" in {
      val result = ReportingFormatter.getOnlineAdjustments(
        onlineAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = None, adjustmentsConfirmed = Some(true),
          onlineTests = Some(AdjustmentDetail(extraTimeNeededNumerical = Some(45))))))
      result mustBe (Some("Numerical extra time needed: 45%"))
    }


    "return other adjustments when needs online adjustments and other adjustments have been requested" in {
      val result = ReportingFormatter.getOnlineAdjustments(
        onlineAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = None, adjustmentsConfirmed = Some(true),
          onlineTests = Some(AdjustmentDetail(otherInfo = Some("other adjustments"))))))
      result mustBe (Some("other adjustments"))
    }

    "return Verbal extra time, numerical extra time needed and other adjustments strings when needs online adjustments " +
      "and extra time for numerical and verbal and other adjustments for online test have been requested" in {
      val result = ReportingFormatter.getOnlineAdjustments(
        onlineAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = None, adjustmentsConfirmed = Some(true),
          onlineTests = Some(AdjustmentDetail(
            extraTimeNeeded = Some(30),
            extraTimeNeededNumerical = Some(45),
            otherInfo = Some("other adjustments"))))))
      result mustBe (Some("Verbal extra time needed: 30%, Numerical extra time needed: 45%, other adjustments"))
    }
  }

  "getAssessmentCentreAdjustments" should {
    "return Some(No) when needs assessment centre adjustments is false" in {
      ReportingFormatter.getAssessmentCentreAdjustments(Some(false), None) mustBe (Some("No"))
    }

    "return None when needs assessment centre adjustments is None" in {
      ReportingFormatter.getAssessmentCentreAdjustments(None, None) mustBe (None)
    }

    "return Some(Yes) when needs assessment centre adjustments is true and adjustments have not been saved" in {
      ReportingFormatter.getAssessmentCentreAdjustments(Some(true), None) mustBe (Some("Yes"))
    }

    "return extra time needed string when needs assessment centre adjustments and extra time for assessment have been requested" in {
      val result = ReportingFormatter.getAssessmentCentreAdjustments(
        assessmentCentreAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = None, adjustmentsConfirmed = Some(true),
          assessmentCenter = Some(AdjustmentDetail(extraTimeNeeded = Some(38))))))
      result mustBe (Some("Extra time needed: 38%"))
    }

    "return other adjustments when needs assessment centre adjustments and other adjustments have been requested" in {
      val result = ReportingFormatter.getAssessmentCentreAdjustments(
        assessmentCentreAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = None, adjustmentsConfirmed = Some(true),
          assessmentCenter = Some(AdjustmentDetail(otherInfo = Some("other adjustments"))))))
      result mustBe (Some("other adjustments"))
    }

    "return extra time and other adjustments strings when needs assessment centre adjustments " +
      "and extra time and other adjustments have been requested" in {
      val result = ReportingFormatter.getAssessmentCentreAdjustments(
        assessmentCentreAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = None, adjustmentsConfirmed = Some(true),
          assessmentCenter = Some(AdjustmentDetail(extraTimeNeeded = Some(27), otherInfo = Some("other adjustments"))))))
      result mustBe (Some("Extra time needed: 27%, other adjustments"))
    }

    "return type of adjustments when there are type of adjustments" in {
      val allTypeOfAdjustments = List("onlineTestsTimeExtension", "onlineTestsOther", "assessmentCenterTimeExtension",
        "coloured paper", "braille test paper", "room alone", "rest breaks", "reader/assistant", "stand up and move around",
        "assessmentCenterOther")
      val result = ReportingFormatter.getAssessmentCentreAdjustments(
        assessmentCentreAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = Some(allTypeOfAdjustments), adjustmentsConfirmed = Some(true))))
      result mustBe (Some("coloured paper, braille test paper, room alone, rest breaks, reader/assistant, stand up and move around"))
    }

    "return type of adjustments and time adjustments when there are type of adjustments and time adjustments" in {
      val allTypeOfAdjustments = List("onlineTestsTimeExtension", "onlineTestsOther", "assessmentCenterTimeExtension",
        "coloured paper", "rest breaks", "reader/assistant", "stand up and move around",
        "assessmentCenterOther")
      val result = ReportingFormatter.getAssessmentCentreAdjustments(
        assessmentCentreAdjustments = Some(true),
        adjustments = Some(Adjustments(typeOfAdjustments = Some(allTypeOfAdjustments),
          adjustmentsConfirmed = Some(true), assessmentCenter = Some(AdjustmentDetail(extraTimeNeeded = Some(15))))))
      result mustBe (Some("Extra time needed: 15%, coloured paper, rest breaks, reader/assistant, stand up and move around"))
    }
  }
}
