/**
 * Copyright 2011 Pablo Mendes, Max Jakob
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dbpedia.spotlight.model

import org.dbpedia.extraction.util.WikiUtil


class SurfaceForm(var name : String) extends Serializable
{

  var annotatedCount: Int = 0
  var totalCount: Int = 0
  var id: Int = 0

  def this(name: String, id: Int, annotatedCount: Int, totalCount: Int) {
    this(name)
    this.id = id
    this.annotatedCount = annotatedCount
    // if there is an error in the counts, otherwise make annotatedCount / totalCount = 0.1, this heuristic applies
    // to both uppercase and lowercase surface forms
    if(annotatedCount > totalCount && (name.headOption match {
        case Some(c: Char) => c.isUpper
        case _ => false
    })) {
      this.totalCount = annotatedCount * 10
    } else {
      if (totalCount < 1) {
          this.totalCount = annotatedCount * 10
      } else {
          this.totalCount = totalCount
      }
    }
    println("New sf - name is: " + name + " annotatedCount: " + annotatedCount + " totalCount: " + totalCount)
  }

  name = name.replace("’", "'")

  name = WikiUtil.cleanSpace(name)

  override def equals(that: Any) = {
    that match {
      case t: SurfaceForm => name.equals(that.asInstanceOf[SurfaceForm].name)
      case _ => false
    }
  }

  override def hashCode(): Int = {
    (if (name != null) name.hashCode else 0)
  }

  def annotationProbability: Double = {

    //TODO: THIS IS HACKISH -> Since we only get the total counts for ngrams (e.g. with n = 5), not
    // all surface forms have a total count. However, it is reasonable that long sf matches are usually
    // annotated, therefore this 1.0

    if (totalCount == -1)
      1.0
    else
      annotatedCount / totalCount.toDouble
  }

  override def toString = "SurfaceForm["+name+"]"
}
