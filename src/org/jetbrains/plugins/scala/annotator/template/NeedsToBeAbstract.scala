package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.annotator.quickfix.ImplementMethodsQuickFix
import org.jetbrains.plugins.scala.annotator.quickfix.modifiers.AddModifierQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.overrideImplement.{ScAliasMember, ScalaOIUtil}
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil._

/**
 * Pavel Fatin
 */

object NeedsToBeAbstract extends AnnotatorPart[ScTemplateDefinition] {
  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    if(!typeAware) return

    if (definition.isInstanceOf[ScNewTemplateDefinition]) return

    if (definition.isInstanceOf[ScObject]) return

    if(isAbstract(definition)) return

    val undefined = for {
      member <- toMembers(getMembersToImplement(definition, withOwn = true))
      if !member.isInstanceOf[ScAliasMember] // See SCL-2887
    } yield (member.getText, member.getParentNodeDelegate.getText)

    if(!undefined.isEmpty) {
      val annotation = holder.createErrorAnnotation(definition.nameId,
        message(kindOf(definition), definition.name, undefined: _*))
      definition match {
        case owner: ScModifierListOwner => annotation.registerFix(new AddModifierQuickFix(owner, "abstract"))
        case _ =>
      }
      if(!ScalaOIUtil.getMembersToImplement(definition).isEmpty) {
        annotation.registerFix(new ImplementMethodsQuickFix(definition))
      }
    }
  }

  def message(kind: String, name: String, members: (String, String)*) = {
    "%s %s needs to be abstract, since %s".format(kind, name,
      members.map(p => " member %s in %s is not defined".format(p._1, p._2)).mkString("; "))
  }
}