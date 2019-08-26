package org.dbpedia.spotlight.spot

import org.dbpedia.spotlight.exceptions.ConfigurationException
import org.dbpedia.spotlight.model.Factory
import org.scalatest._

class SpotSelectorTest extends FlatSpec with Matchers {

    "SpotSelector Factory" should "create a simple selector" in {
        Factory.SpotSelector.fromName("ShortSurfaceFormSelector")
    }

    it should "create one selector from a list" in {
        Factory.SpotSelector.fromNameList("AtLeastOneNounSelector").size==1
    }

    it should "create multiple selectors" in {
        Factory.SpotSelector.fromNameList("ShortSurfaceFormSelector,AtLeastOneNounSelector").size==2
    }

    it should "throw an exception when a name does not exist" in {
        an [ConfigurationException] should be thrownBy { Factory.SpotSelector.fromName("fff") }
    }

    it should "throw an exception when the name is empty" in {
        an [IllegalArgumentException] should be thrownBy  { Factory.SpotSelector.fromName("") }
    }

}

