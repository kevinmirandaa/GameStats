package gamestats.analytics.analysis.games

object Statistics:

  def mean(values: List[Double]): Option[Double] =
    Option.when(values.nonEmpty)(values.sum / values.size)

  def populationStandardDeviation(values: List[Double]): Option[Double] =
    mean(values).map { average =>
      math.sqrt(values.map(value => math.pow(value - average, 2)).sum / values.size)
    }

  def definedOnly(entries: (String, Option[Double])*): Map[String, Double] =
    entries.collect { case (name, Some(value)) => name -> value }.toMap
