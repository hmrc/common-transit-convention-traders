/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utils

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import scala.xml.Node
import scala.xml.Utility.trim

class XmlHelperSpec extends AnyFreeSpec with Matchers {

  object TestXmlHelper$ extends XmlHelper

  "Valid XML" - {

    "without a namespace that is unaltered" in {
      val input = <root>
        <child>test</child>
      </root>

      TestXmlHelper$.stripNamespaceFromRoot(input) mustEqual input
    }

    "with a namespace that is altered" in {
      val inputs = Seq(
        <root xmlns:xsi="test">
          <child>test</child>
        </root>,
        <root xmlns:xsd="test">
          <child>test</child>
        </root>,
        <root xmlns:xsd="test" xmlns:xsi="test">
          <child>test</child>
        </root>,
        <root xmlns:xsd="test" xmlns:xsi="test">
          <child xmlns:xsd="test2" xmlns:xsi="test2">test</child>
        </root>
      )

      val expected = <root>
        <child>test</child>
      </root>

      inputs.foreach(
        in => trim(TestXmlHelper$.stripNamespaceFromRoot(in).asInstanceOf[Node]) mustEqual trim(expected)
      )
    }
  }
}
