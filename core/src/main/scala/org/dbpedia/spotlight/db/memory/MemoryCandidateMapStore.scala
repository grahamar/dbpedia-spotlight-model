package org.dbpedia.spotlight.db.memory

import org.dbpedia.spotlight.db.model.{CandidateMapStore, ResourceStore}
import org.dbpedia.spotlight.model.{Candidate, SurfaceForm}

/**
 *
 *
 * @author Joachim Daiber
 */

@SerialVersionUID(1005001)
class MemoryCandidateMapStore
  extends MemoryStore
  with CandidateMapStore {

  var candidates      = Array[Array[Int]]()
  var candidateCounts = Array[Array[Short]]()

  def size = candidates.size

  @transient
  var resourceStore: ResourceStore = null

  def getCandidates(surfaceform: SurfaceForm): Set[Candidate] = {
    try {
//      println("surface form: " + surfaceform)
      candidates(surfaceform.id).zip(candidateCounts(surfaceform.id)).map {
            case (resID, count) => new Candidate(surfaceform, resourceStore.getResource(resID), qc(count))
      }.toSet
    } catch {
      case e: NullPointerException => Set[Candidate]()
    }
  }

}
