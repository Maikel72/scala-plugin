package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.navigation.NavigationItem
import com.intellij.psi.impl.ResolveScopeManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import statements._
import toplevel.templates.ScTemplateBody
import toplevel.{ScEarlyDefinitions, ScNamedElement, ScTypedDefinition}
import extensions.toPsiNamedElementExt
import toplevel.typedef.{ScTemplateDefinition, ScMember, ScTypeDefinition}

trait ScBindingPattern extends ScPattern with ScNamedElement with ScTypedDefinition with NavigationItem {
  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def isWildcard: Boolean

  override def getUseScope = {
    val func = PsiTreeUtil.getContextOfType(this, true, classOf[ScFunctionDefinition])
    if (func != null) new LocalSearchScope(func) else ResolveScopeManager.getElementUseScope(this)
  }

  protected def getEnclosingVariable = {
    def goUpper(e: PsiElement): Option[ScVariable] = e match {
      case _ : ScPattern => goUpper(e.getParent)
      case _ : ScPatternArgumentList => goUpper(e.getParent)
      case v: ScVariable => Some(v)
      case _ => None
    }

    goUpper(this)
  }

  override def isStable = getEnclosingVariable match {
    case None => true
    case _ => false
  }

  override def isVar: Boolean = nameContext.isInstanceOf[ScVariable]
  override def isVal: Boolean = nameContext.isInstanceOf[ScValue]

  def isClassMember = nameContext.getContext match {
    case _: ScTemplateBody | _: ScEarlyDefinitions => true
    case _ => false
  }
  def isBeanProperty: Boolean = nameContext match {
    case a: ScAnnotationsHolder => a.hasAnnotation("scala.reflect.BeanProperty") != None
    case _ => false
  }

  def containingClass: ScTemplateDefinition = {
    ScalaPsiUtil.nameContext(this) match {
      case memb: ScMember => memb.containingClass
      case _ => null
    }
  }


  def getOriginalElement: PsiElement = {
    val ccontainingClass = containingClass
    if (ccontainingClass == null) return this
    val originalClass: PsiClass = ccontainingClass.getOriginalElement.asInstanceOf[PsiClass]
    if (ccontainingClass eq originalClass) return this
    if (!originalClass.isInstanceOf[ScTypeDefinition]) return this
    val c = originalClass.asInstanceOf[ScTypeDefinition]
    val membersIterator = c.members.iterator
    while (membersIterator.hasNext) {
      val member = membersIterator.next()
      member match {
        case _: ScValue | _: ScVariable =>
          val d = member.asInstanceOf[ScDeclaredElementsHolder]
          val elemsIterator = d.declaredElements.iterator
          while (elemsIterator.hasNext) {
            val nextElem = elemsIterator.next()
            if (nextElem.name == name) return nextElem
          }
        case _ =>
      }
    }
    this
  }
}