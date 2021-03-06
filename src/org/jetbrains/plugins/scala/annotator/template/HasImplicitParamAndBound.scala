package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._

object HasImplicitParamAndBound extends AnnotatorPart[ScClass] {
  def kind = classOf[ScClass]

  def annotate(definition: ScClass, holder: AnnotationHolder, typeAware: Boolean) {
    definition.constructor match {
      case Some(const) => checkImplicitParametersAndBounds(definition, Some(const.parameterList), holder)
      case None =>
    }
  }
}