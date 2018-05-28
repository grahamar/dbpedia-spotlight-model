package org.dbpedia.spotlight.db

import breeze.linalg
import org.dbpedia.spotlight.db.model._
import org.dbpedia.spotlight.db.similarity.ContextSimilarity
import org.dbpedia.spotlight.disambiguate.ParagraphDisambiguator
import org.dbpedia.spotlight.disambiguate.mixtures.Mixture
import org.dbpedia.spotlight.exceptions.InputException
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model._
import org.dbpedia.spotlight.util.MathUtil

import scala.Predef._
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer


/**
 * A database-backed paragraph disambiguator working in two steps:
 * candidate search and disambiguation.
 *
 * Candidates are searched using a [[org.dbpedia.spotlight.db.DBCandidateSearcher]].
 * For each candidate, we calculate scores, including the context similarity, which
 * are then combined in a Mixture and assigned to the [[org.dbpedia.spotlight.model.DBpediaResourceOccurrence]].
 *
 * @author Joachim Daiber
 * @author pablomendes (Lucene-based TwoStepDisambiguator)
 */

class DBTwoStepDisambiguator(
  tokenStore: TokenTypeStore,
  surfaceFormStore: SurfaceFormStore,
  resourceStore: ResourceStore,
  val candidateSearcher: DBCandidateSearcher,
  mixture: Mixture,
  contextSimilarity: ContextSimilarity
) extends ParagraphDisambiguator {

  /* Tokenizer that may be used for tokenization if the text is not already tokenized. */
  var tokenizer: TextTokenizer = null


  //maximum number of considered candidates
  val MAX_CANDIDATES = 10

  //maximum context window in tokens in both directions
  val MAX_CONTEXT = 200


  def bestK(paragraph: Paragraph, k: Int): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {

    SpotlightLog.debug(this.getClass, "Running bestK for paragraph %s.",paragraph.id)

    if (paragraph.occurrences.size == 0)
      return Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]]()

    //Tokenize the text if it wasn't tokenized before:
    if (tokenizer != null) {
      SpotlightLog.info(this.getClass, "Tokenizing input text...")
      val tokens = tokenizer.tokenize(paragraph.text)
      paragraph.text.setFeature(new Feature("tokens", tokens))
    }

    val sentences = DBSpotter.tokensToSentences(paragraph.text.featureValue[List[Token]]("tokens").get)

    if (sentences.size <= MAX_CONTEXT)
      bestK_(paragraph, paragraph.getOccurrences().toList, sentences.flatMap(_.map(_.tokenType)), k)
    else {
      val occurrenceStack = paragraph.getOccurrences().toBuffer
      val currentTokens = ArrayBuffer[Token]()

      val res: Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = sentences.flatMap{
        sentence: List[Token] =>

          currentTokens ++= sentence

          if (currentTokens.size >= MAX_CONTEXT || sentence.equals(sentences.last)) {

            //Take all surface form occurrences within the current token window and remove them afterwards.
            val sliceOccs = occurrenceStack.takeWhile{ occ: SurfaceFormOccurrence => occ.textOffset <= currentTokens.last.offset}.toList
            occurrenceStack.remove(0, sliceOccs.size)

            //Remember the tokens and clear the temporary token collection:
            val sliceTokens = currentTokens.map(_.tokenType)
            currentTokens.clear()

            //Disambiguate all occs in the current window:
            Some( bestK_(paragraph, sliceOccs, sliceTokens, k) )
          } else {
            None
          }
      }.toList.reduce(_ ++ _)
      res
    }
  }


  def bestK_(paragraph: Paragraph, occurrences: List[SurfaceFormOccurrence], tokens: Seq[TokenType], k: Int): Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]] = {

    if (occurrences.size == 0)
      return Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]]()

    // step1: get candidates for all surface forms
    var allCandidateResources = Set[DBpediaResource]()
    val occs = occurrences.foldLeft(
      Map[SurfaceFormOccurrence, List[Candidate]]())(
      (acc, sfOcc) => {

        SpotlightLog.debug(this.getClass, "Searching...")

        val candidateRes = {

          val cands = candidateSearcher.getCandidates(sfOcc.surfaceForm)
          SpotlightLog.debug(this.getClass, "# candidates for: %s = %s.", sfOcc.surfaceForm, cands.size)
          //println("# candidates for: %s = %s.", sfOcc.surfaceForm, cands.size)

          if (cands.size > MAX_CANDIDATES) {
            SpotlightLog.debug(this.getClass, "Reducing number of candidates to %d.", MAX_CANDIDATES)
            //println("Reducing number of candidates to %d.", MAX_CANDIDATES)
            cands.toList.sortBy( -_.prior ).take(MAX_CANDIDATES).toSet
          } else {
            cands
          }
        }
        //println("Took candidates: " + candidateRes)

        allCandidateResources ++= candidateRes.map(_.resource)
        acc + (sfOcc -> candidateRes.toList)
      })

    val tokensDistinct = tokens.distinct.sortBy(_.id)

    // step2: query once for the paragraph context, get scores for each candidate resource
    val contextScores = contextSimilarity.score(tokensDistinct, allCandidateResources)

    // pick the best k for each surface form
    occs.keys.foldLeft(Map[SurfaceFormOccurrence, List[DBpediaResourceOccurrence]]())( (acc, aSfOcc) => {

      //Get the NIL entity:
      val eNIL = new DBpediaResourceOccurrence(
        new DBpediaResource("--nil--"),
        aSfOcc.surfaceForm,
        paragraph.text,
        -1
      )

      //val nilContextScore = contextSimilarity.nilScore(tokensDistinct) / tokensDistinct.length
      val nilContextScore = contextSimilarity.nilScore(tokensDistinct) / tokensDistinct.take(10).length

      aSfOcc.featureValue[Array[TokenType]]("token_types") match {
        case Some(t) => eNIL.setFeature(new Score("P(s|e)", contextSimilarity.nilScore(t)))
        case _ =>
      }


      eNIL.setFeature(new Score("P(c|e)", nilContextScore))
      eNIL.setFeature(new Score("P(e)",   MathUtil.ln( 1 / surfaceFormStore.getTotalAnnotatedCount.toDouble ) )) //surfaceFormStore.getTotalAnnotatedCount = total number of entity mentions
      val nilEntityScore = mixture.getScore(eNIL)

      //Get all other entities:
      val candOccs = occs.getOrElse(aSfOcc, List[Candidate]())
        .map{ cand: Candidate => {
        val resOcc = new DBpediaResourceOccurrence(
          "",
          cand.resource,
          cand.surfaceForm,
          aSfOcc.context,
          aSfOcc.textOffset,
          Provenance.Undefined,
          0.0,
          0.0,
          contextScores(cand.resource)
        )

        // Set the scores as features for the resource occurrence:
        // Note that this is not mathematically correct, since the candidate prior is P(e|s),
        // the correct P(s|e) should be MathUtil.ln( cand.support / cand.resource.support.toDouble )
        resOcc.setFeature(new Score("P(s|e)", MathUtil.ln( cand.prior )))
        resOcc.setFeature(new Score("P(c|e)", resOcc.contextualScore))
        resOcc.setFeature(new Score("P(e)",   MathUtil.ln( cand.resource.prior )))

        resOcc.setEntityScore(MathUtil.ln(cand.resource.prior))
        resOcc.setSFScore(MathUtil.ln(cand.prior))

        // Use the mixture to combine the scores
        // Chris: below we will pass feature columns through softmax 
        resOcc.setSimilarityScore(mixture.getScore(resOcc))
        resOcc
      }
      }
        .filter{ o => !java.lang.Double.isNaN(o.similarityScore) && o.similarityScore > nilEntityScore }
        .sortBy( o => o.similarityScore )
        .reverse
        .take(k)

      (1 to candOccs.size-1).foreach{ i: Int =>
        val top = candOccs(i-1)
        val bottom = candOccs(i)
        top.setPercentageOfSecondRank(MathUtil.exp(bottom.similarityScore - top.similarityScore))
      }

      //val cScores = candOccs.map(_.contextualScore) :+ -1.0
      //val contextMin = cScores.min
      //val contextMax = cScores.max
      //val contextDenom = contextMax - contextMin
      //val normContextScores = cScores.map(v => (v - contextMin) / contextDenom)

      //val contextSoftMaxTotal    = linalg.softmax(normContextScores)

      // Chris: note omission of nilSFScore here, because it's computed as Option[] above
      //val sfScores = candOccs.map(_.SFScore) :+ -1.0
      //val sfMin = sfScores.min
      //val sfMax = sfScores.max
      //val sfDenom = sfMax - sfMin
      //val normSfScores = sfScores.map(v => (v - sfMin) / sfDenom)
      //val sfSoftMaxTotal     = linalg.softmax(normSfScores)


      //val entityScores = candOccs.map(_.entityScore) :+ -1.0
      //val entityMin = entityScores.min
      //val entityMax = entityScores.max
      //val entityDenom = entityMax - entityMin
      //val normEntityScores = entityScores.map(v => (v - entityMin) / entityDenom)
      //val entitySoftMaxTotal     = linalg.softmax(normEntityScores)

      candOccs.foreach{ o: DBpediaResourceOccurrence =>
        // Chris: now recompute scores with softmax over each column
        // o.setContextualScore( MathUtil.exp(((o.contextualScore - contextMin) / contextDenom)  - contextSoftMaxTotal) * 2.0)    // e^xi / \sum e^xi
        o.setFeature(new Score("P(c|e)", o.contextualScore))

        o.setFeature(new Score("P(s|e)", o.SFScore))
        // o.setFeature(new Score("P(s|e)", MathUtil.exp(((o.SFScore - sfMin) / sfDenom) - sfSoftMaxTotal) * 1.0))
        o.setFeature(new Score("P(e)", o.entityScore))

        // Use the mixture to recompute the scores
        o.setSimilarityScore(mixture.getScore(o))

      }

      if (candOccs.size > 0) {
        val similaritySoftMaxTotal = linalg.softmax(candOccs.map(_.similarityScore))
        candOccs.foreach{ o: DBpediaResourceOccurrence =>
          o.setSimilarityScore(MathUtil.exp(o.similarityScore - similaritySoftMaxTotal))
        }
      }

      val finalOccs = candOccs.sortBy( o => o.similarityScore ).reverse
      //println("printing sorted occs...")
      //finalOccs.foreach{ o: DBpediaResourceOccurrence =>
      //  println("occ: " + o)
      //}

      acc + (aSfOcc -> finalOccs)
    })


  }


  @throws(classOf[InputException])
  def disambiguate(paragraph: Paragraph): List[DBpediaResourceOccurrence] = {
    // return first from each candidate set
    bestK(paragraph, MAX_CANDIDATES)
      .filter(kv =>
      kv._2.nonEmpty)
      .map( kv =>
      kv._2.head)
      .toList
      .sortBy(_.textOffset)
  }

  def name = "Database-backed 2 Step disambiguator (%s, %s)".format(contextSimilarity.getClass.getSimpleName, mixture.toString)

}
