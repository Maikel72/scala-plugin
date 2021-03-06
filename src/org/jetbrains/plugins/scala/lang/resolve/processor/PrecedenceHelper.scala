package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import com.intellij.psi.{PsiElement, PsiMember, PsiPackage, PsiClass}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult}
import collection.mutable.HashSet
import org.jetbrains.plugins.scala.extensions.toPsiClassExt

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.12.11
 */

trait PrecedenceHelper[T] {
  this: BaseProcessor =>

  protected def getPlace: PsiElement
  protected lazy val placePackageName: String = ResolveUtils.getPlacePackage(getPlace)
  protected val levelSet: java.util.HashSet[ScalaResolveResult] = new java.util.HashSet
  protected val qualifiedNamesSet: HashSet[T] = new HashSet[T]
  protected val levelQualifiedNamesSet: HashSet[T] = new HashSet[T]

  protected def getQualifiedName(result: ScalaResolveResult): T

  /**
   * Returns highest precedence of all resolve results.
   * 1 - import a._
   * 2 - import a.x
   * 3 - definition or declaration
   */
  protected def getTopPrecedence(result: ScalaResolveResult): Int
  protected def setTopPrecedence(result: ScalaResolveResult, i: Int)
  protected def filterNot(p: ScalaResolveResult, n: ScalaResolveResult): Boolean = {
    getPrecedence(p) < getTopPrecedence(n)
  }
  protected def isCheckForEqualPrecedence = true

  /**
   * Do not add ResolveResults through candidatesSet. It may break precedence. Use this method instead.
   */
  protected def addResult(result: ScalaResolveResult): Boolean = addResults(Seq(result))
  protected def addResults(results: Seq[ScalaResolveResult]): Boolean = {
    if (results.length == 0) return true
    lazy val qualifiedName: T = getQualifiedName(results(0))
    def addResults() {
      if (qualifiedName != null) levelQualifiedNamesSet += qualifiedName
      val iterator = results.iterator
      while (iterator.hasNext) {
        levelSet.add(iterator.next())
      }
    }
    val currentPrecedence = getPrecedence(results(0))
    val topPrecedence = getTopPrecedence(results(0))
    if (currentPrecedence < topPrecedence) return false
    else if (currentPrecedence == topPrecedence && levelSet.isEmpty) return false
    else if (currentPrecedence == topPrecedence && !levelSet.isEmpty) {
      if (isCheckForEqualPrecedence && qualifiedName != null &&
        (levelQualifiedNamesSet.contains(qualifiedName) ||
        qualifiedNamesSet.contains(qualifiedName))) {
        return false
      } else if (qualifiedName != null && qualifiedNamesSet.contains(qualifiedName)) return false
      addResults()
    } else {
      if (qualifiedName != null && (levelQualifiedNamesSet.contains(qualifiedName) ||
        qualifiedNamesSet.contains(qualifiedName))) {
        return false
      } else {
        setTopPrecedence(results(0), currentPrecedence)
        val levelSetIterator = levelSet.iterator()
        while (levelSetIterator.hasNext) {
          val next = levelSetIterator.next()
          if (filterNot(next, results(0))) {
            levelSetIterator.remove()
          }
        }
        levelQualifiedNamesSet.clear()
        addResults()
      }
    }
    true
  }

  protected def getPrecedence(result: ScalaResolveResult): Int = {
    def getPackagePrecedence(qualifier: String): Int = {
      if (qualifier == null) return 6
      val index: Int = qualifier.lastIndexOf('.')
      if (index == -1) return 3
      val q = qualifier.substring(0, index)
      if (q == "java.lang") return 1
      else if (q == "scala") return 2
      else if (q == placePackageName) return 6
      else return 3
    }
    def getClazzPrecedence(clazz: PsiClass): Int = {
      val qualifier = clazz.qualifiedName
      if (qualifier == null) return 6
      val index: Int = qualifier.lastIndexOf('.')
      if (index == -1) return 6
      val q = qualifier.substring(0, index)
      if (q == "java.lang") return 1
      else if (q == "scala") return 2
      else if (PsiTreeUtil.isContextAncestor(clazz.getContainingFile, getPlace, true)) return 6
      else return 3
    }
    if (result.importsUsed.size == 0) {
      ScalaPsiUtil.nameContext(result.getActualElement) match {
        case synthetic: ScSyntheticClass => return 2 //like scala.Int
        case obj: ScObject if obj.isPackageObject => {
          val qualifier = obj.qualifiedName
          return getPackagePrecedence(qualifier)
        }
        case pack: PsiPackage => {
          val qualifier = pack.getQualifiedName
          return getPackagePrecedence(qualifier)
        }
        case clazz: PsiClass => {
          return getClazzPrecedence(clazz)
        }
        case _: ScBindingPattern | _: PsiMember => {
          val clazzStub = ScalaPsiUtil.getContextOfType(result.getActualElement, false, classOf[PsiClass])
          val clazz: PsiClass = clazzStub match {
            case clazz: PsiClass => clazz
            case _ => null
          }
          //val clazz = PsiTreeUtil.getParentOfType(result.getActualElement, classOf[PsiClass])
          if (clazz == null) return 6
          else {
            clazz.qualifiedName match {
              case "scala.Predef" => return 2
              case "scala.LowPriorityImplicits" => return 2
              case "scala" => return 2
              case _ => return 6
            }
          }
        }
        case _ =>
      }
      return 6
    }
    val importsUsedSeq = result.importsUsed.toSeq
    val importUsed: ImportUsed = importsUsedSeq.apply(importsUsedSeq.length - 1)
    // TODO this conflates imported functions and imported implicit views. ScalaResolveResult should really store
    //      these separately.
    importUsed match {
      case _: ImportWildcardSelectorUsed => 4
      case _: ImportSelectorUsed => 5
      case ImportExprUsed(expr) => {
        if (expr.singleWildcard) 4
        else 5
      }
    }
  }
}