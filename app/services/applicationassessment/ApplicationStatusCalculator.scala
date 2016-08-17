package services.applicationassessment

import model.ApplicationStatuses
import model.EvaluationResults._

trait ApplicationStatusCalculator {

  def determineStatus(result: AssessmentRuleCategoryResult): String = result.passedMinimumCompetencyLevel match {
    case Some(false) =>
      ApplicationStatuses.AssessmentCentreFailed
    case _ =>
      val allResultsOrderedByPreferrences = List(result.location1Scheme1, result.location1Scheme2,
        result.location2Scheme1, result.location2Scheme2, result.alternativeScheme).flatten
      statusBasedOnFirstNonRedResult(allResultsOrderedByPreferrences)
  }

  private def statusBasedOnFirstNonRedResult(allResultsInPreferrenceOrder: List[Result]) = {
    val amberOrGreenOnly = allResultsInPreferrenceOrder filterNot (_ == Red)

    amberOrGreenOnly.headOption match {
      case Some(Green) => ApplicationStatuses.AssessmentCentrePassed
      case Some(Amber) => ApplicationStatuses.AwaitingAssessmentCentreReevaluation
      case None => ApplicationStatuses.AssessmentCentreFailed
    }

  }

}
