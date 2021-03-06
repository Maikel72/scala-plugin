package org.jetbrains.plugins.scala.debugger

import com.intellij.debugger.actions.JvmSmartStepIntoHandler
import com.intellij.debugger.SourcePosition
import evaluation.util.DebuggerUtil
import java.util.{List => JList}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.CharArrayUtil
import com.intellij.psi._
import collection.mutable.HashSet
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScDesignatorType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScParameterizedTypeElement}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.debugger.engine.RequestHint.SmartStepFilter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor

/**
 * User: Alexander Podkhalyuzin
 * Date: 26.01.12
 */

class ScalaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
  def findReferencedMethods(position: SourcePosition): JList[PsiMethod] = {
    import scala.collection.JavaConversions.seqAsJavaList
    findReferencedMethodsScala(position)
  }
  
  def isAvailable(position: SourcePosition): Boolean = {
    val file: PsiFile = position.getFile
    file.isInstanceOf[ScalaFile]
  }

  private def findReferencedMethodsScala(position: SourcePosition): List[PsiMethod] = {
    val line: Int = position.getLine
    if (line < 0) return List.empty
    val file: PsiFile = position.getFile
    if (!file.isInstanceOf[ScalaFile]) return List.empty
    val scalaFile = file.asInstanceOf[ScalaFile]
    if (scalaFile.isCompiled) return List.empty
    val vFile: VirtualFile = file.getVirtualFile
    if (vFile == null) return List.empty
    val document: Document = FileDocumentManager.getInstance.getDocument(vFile)
    if (document == null) return List.empty
    if (line >= document.getLineCount) return List.empty
    val startOffset: Int = document.getLineStartOffset(line)
    val lineRange: TextRange = new TextRange(startOffset, document.getLineEndOffset(line))
    val offset: Int = CharArrayUtil.shiftForward(document.getCharsSequence, startOffset, " \t")
    var element: PsiElement = file.findElementAt(offset)
    if (element == null) return List.empty
    while (element.getParent != null && element.getParent.getTextRange.getStartOffset >= lineRange.getStartOffset) {
      element = element.getParent
    }
    val methods: HashSet[PsiMethod] = new HashSet[PsiMethod]()

    class MethodsVisitor extends ScalaRecursiveElementVisitor {
      override def visitExpression(expr: ScExpression) {
        expr.getImplicitConversions() match {
          case (_, Some(f: PsiMethod)) => methods += f
          case (_, Some(t: ScTypedStmt)) => ScType.extractFunctionType(t.getType(TypingContext.empty).getOrAny) match {
            case Some(f) =>
              f.resolveFunctionTrait match {
                case Some(ScParameterizedType(ScDesignatorType(funTrait: ScTrait), _)) =>
                  ScType.extractClass(t.getType(TypingContext.empty).get) match {
                    case Some(clazz: ScTypeDefinition) =>
                      val funApply = funTrait.functionsByName("apply").apply(0)
                      clazz.allMethods.foreach((signature: PhysicalSignature) => {
                        signature.method match {
                          case fun: ScFunction if fun.name == "apply" && fun.superMethods.contains(funApply) =>
                            methods += fun
                          case _ =>
                        }
                      })
                    case _ =>
                  }
                case _ =>
              }
            case _ =>
          }
          case _ =>
        }
        
        expr match {
          case n: ScNewTemplateDefinition if n.extendsBlock.templateBody != None =>
            return //ignore anonymous classes
          case n: ScNewTemplateDefinition =>
            n.extendsBlock.templateParents match {
              case Some(tp) =>
                tp.typeElements.headOption match {
                  case Some(te) =>
                    val constr = te match {
                      case p: ScParameterizedTypeElement => p.findConstructor
                      case s: ScSimpleTypeElement => s.findConstructor
                      case _ => None
                    }
                    constr match {
                      case Some(constr) =>
                        constr.reference match {
                          case Some(ref) =>
                            ref.bind() match {
                              case Some(ScalaResolveResult(f: PsiMethod, _)) => methods += f
                              case _ =>
                            }
                          case _ =>
                        }
                      case _ =>
                    }
                  case _ =>
                }
              case _ =>
            }
          case expr if ScUnderScoreSectionUtil.isUnderscoreFunction(expr) =>
            return //ignore clojures
          case ref: ScReferenceExpression =>
            ref.resolve() match {
              case fun: PsiMethod => methods += fun
              case _ =>
            }
          case f: ScForStatement =>
            f.getDesugarisedExpr match {
              case Some(expr) =>
                expr.accept(new MethodsVisitor)
                return
              case _ =>
            }
          case f: ScFunctionExpr =>
            return //ignore closures
          case b: ScBlock if b.isAnonymousFunction =>
            return //ignore closures
          case _ =>
        }
        super.visitExpression(expr)
      }
    }
    
    val methodCollector: PsiElementVisitor = new MethodsVisitor
    element.accept(methodCollector)

    element = element.getNextSibling
    while (element != null && !lineRange.intersects(element.getTextRange)) {
      element.accept(methodCollector)
      element = element.getNextSibling
    }
    methods.toList
  }

  override def getSmartStepFilter(method: PsiMethod): SmartStepFilter = {
    method match {
      case f: ScFunction =>
        val clazz = f.getContainingClass
        new SmartStepFilter(DebuggerUtil.getClassJVMName(clazz, true), method.getName, DebuggerUtil.getFunctionJVMSignature(f))
      case f: ScPrimaryConstructor =>
        val clazz = f.getContainingClass
        new SmartStepFilter(DebuggerUtil.getClassJVMName(clazz, true), "<init>", DebuggerUtil.getFunctionJVMSignature(f))
      case _ => super.getSmartStepFilter(method)
    }
  }
}