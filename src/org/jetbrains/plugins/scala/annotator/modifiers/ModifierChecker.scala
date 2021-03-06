package org.jetbrains.plugins.scala
package annotator
package modifiers

import _root_.scala.collection.mutable.HashSet
import com.intellij.psi.util.PsiTreeUtil
import lang.psi.api.statements.params.{ScParameter, ScClassParameter}
import lang.psi.api.toplevel.typedef._
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.base.{ScAccessModifier, ScModifierList}
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import AnnotatorUtils._
import quickfix.modifiers.RemoveModifierQuickFix
import lang.psi.api.statements.{ScTypeAlias, ScPatternDefinition, ScDeclaration}
import lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner}

/**
 * @author Aleksander Podkhalyuzin
 * @date 25.03.2009
 */

private[annotator] object ModifierChecker {
  def checkModifiers(ml: ScModifierList, holder: AnnotationHolder) {
    if (!ml.getParent.isInstanceOf[ScModifierListOwner]) {
      return
    }
    val owner = ml.getParent.asInstanceOf[ScModifierListOwner]
    val modifiersSet = new HashSet[String]
    def checkDublicates(element: PsiElement, text: String): Boolean = checkDublicate(element, text, false)
    def checkDublicate(element: PsiElement, text: String, withPrivate: Boolean): Boolean = {
      val illegalCombinations = Array[(String, String)](
        ("final", "sealed"),
        if (withPrivate) ("final", "private") else ("", ""),
        ("private", "protected"),
        if (withPrivate) ("private", "override") else ("", "")
        )
      for ((bad1, bad2) <- illegalCombinations if (bad1 == text && owner.hasModifierProperty(bad2)) ||
              (bad2 == text && owner.hasModifierProperty(bad1))) {
        proccessError(ScalaBundle.message("illegal.modifiers.combination", bad1, bad2), element, holder,
          new RemoveModifierQuickFix(owner, text))
        return false
      }
      if (modifiersSet.contains(text)) {
        proccessError(ScalaBundle.message("illegal.modifiers.combination", text, text), element, holder,
          new RemoveModifierQuickFix(owner, text))
        false
      } else {
        modifiersSet += text
        true
      }
    }
    for (modifier <- ml.getNode.getChildren(null)) {
      val modifierPsi = modifier.getPsi
      modifierPsi match {
        case am: ScAccessModifier => { //todo: check private with final or sealed combination.
          if (am.isPrivate) {
            checkDublicates(am, "private")
          } else if (am.isProtected) {
            checkDublicates(am, "protected")
          }
        }
        case _ => {
          modifier.getText match {
            case "lazy" => {
              owner match {
                case _: ScPatternDefinition => checkDublicates(modifierPsi, "lazy")
                case _: ScParameter => {
                  proccessError(ScalaBundle.message("lazy.modifier.is.not.allowed.with.param"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "lazy"))
                }
                case _ => {
                  proccessError(ScalaBundle.message("lazy.modifier.is.not.allowed.here"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "lazy"))
                }
              }
            }
            case "final" => {
              owner match {
                case _: ScDeclaration => {
                  proccessError(ScalaBundle.message("final.modifier.not.with.declarations"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "final"))
                }
                case _: ScTrait => {
                  proccessError(ScalaBundle.message("final.modifier.not.with.trait"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "final"))
                }
                case _: ScClass => checkDublicates(modifierPsi, "final")
                case _: ScObject => {
                  if (checkDublicates(modifierPsi, "final")) {
                    proccessWarning(ScalaBundle.message("final.modifier.is.redundant.with.object"), modifierPsi, holder,
                      new RemoveModifierQuickFix(owner, "final"))
                  }
                }
                case e: ScMember if e.getParent.isInstanceOf[ScTemplateBody] || e.getParent.isInstanceOf[ScEarlyDefinitions] => {
                  val redundant = (e.containingClass, e) match {
                    case (obj: ScObject, valMember: ScPatternDefinition) if valMember.typeElement.isEmpty &&
                            valMember.pList.allPatternsSimple => false // SCL-899
                    case (cls, _) if cls.hasModifierProperty("final") => true
                    case _ => false
                  }
                  if (redundant) {
                    if (checkDublicates(modifierPsi, "final")) {
                      proccessWarning(ScalaBundle.message("final.modifier.is.redundant.with.final.parents"), modifierPsi, holder,
                        new RemoveModifierQuickFix(owner, "final"))
                    }
                  } else {
                    checkDublicates(modifierPsi, "final")
                  }
                }
                case e: ScClassParameter => {
                  if (PsiTreeUtil.getParentOfType(e, classOf[ScTypeDefinition]).hasModifierProperty("final")) {
                    if (checkDublicates(modifierPsi, "final")) {
                      proccessWarning(ScalaBundle.message("final.modifier.is.redundant.with.final.parents"), modifierPsi, holder,
                        new RemoveModifierQuickFix(owner, "final"))
                    }
                  } else {
                    checkDublicates(modifierPsi, "final")
                  }
                }
                case _ => {
                  proccessError(ScalaBundle.message("final.modifier.is.not.allowed.here"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "final"))
                }
              }
            }
            case "sealed" => {
              owner match {
                case _: ScClass | _: ScTrait | _: ScClassParameter => checkDublicates(modifierPsi, "sealed")
                case e: ScMember if e.getParent.isInstanceOf[ScTemplateBody] => checkDublicates(modifierPsi, "sealed")
                case _ => {
                  proccessError(ScalaBundle.message("sealed.modifier.is.not.allowed.here"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "sealed"))
                }
              }
            }
            case "abstract" => {
              owner match {
                case _: ScClass => checkDublicates(modifierPsi, "abstract")
                case _: ScTrait => if (checkDublicates(modifierPsi, "abstract")) {
                  proccessWarning(ScalaBundle.message("abstract.modifier.redundant.fot.traits"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "abstract"))
                }
                case member: ScMember if !member.isInstanceOf[ScTemplateBody] &&
                        member.getParent.isInstanceOf[ScTemplateBody] => {
                  // 'abstract override' modifier only allowed for members of traits
                  if (!member.containingClass.isInstanceOf[ScTrait] && owner.hasModifierProperty("override")) {
                    proccessError(ScalaBundle.message("abstract.override.modifier.is.not.allowed"), modifierPsi, holder,
                      new RemoveModifierQuickFix(owner, "abstract"))
                  } else {
                    checkDublicates(modifierPsi, "abstract")
                  }
                }
                case _ => {
                  proccessError(ScalaBundle.message("abstract.modifier.is.not.allowed"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "abstract"))
                }
              }
            }
            case "override" => {
              owner match {
                case _: ScTypeDefinition => {
                  proccessError(ScalaBundle.message("override.modifier.is.not.allowed.for.classes"), modifierPsi,
                    holder, new RemoveModifierQuickFix(owner, "override"))
                }
                case member: ScMember if member.getParent.isInstanceOf[ScTemplateBody] ||
                  member.getParent.isInstanceOf[ScEarlyDefinitions] => {
                  checkDublicates(modifierPsi, "override")
                }
                case param: ScClassParameter => checkDublicates(modifierPsi, "override")
                case _ => {
                  proccessError(ScalaBundle.message("override.modifier.is.not.allowed"), modifierPsi, holder,
                    new RemoveModifierQuickFix(owner, "override"))
                }
              }
            }
            case "implicit" => {
              owner match {
                case _: ScClass | _: ScTrait | _: ScTypeAlias => {
                  proccessError("'implicit' modifier can be used only for values, variables and methods",
                    modifierPsi, holder, new RemoveModifierQuickFix(owner, "implicit"))
                }
                case _ => checkDublicates(modifierPsi, "implicit")
              }
            }
            case _ =>
          }
        }
      }
    }
  }
}