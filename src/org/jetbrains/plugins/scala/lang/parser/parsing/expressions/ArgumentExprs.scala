package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package expressions

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes
import nl.LineTerminator
import builder.ScalaPsiBuilder

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

/*
 * ArgumentExprs ::= '(' [Exprs [',']] ')'
 *                 | [nl] BlockExpr
 */

object ArgumentExprs {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val argMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate (
        builder.disableNewlines
        Expr parse builder
        while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
          builder.advanceLexer
          if (!Expr.parse(builder)) {
            builder error ErrMsg("wrong.expression")
          }
        }
        builder.getTokenType match {
          case ScalaTokenTypes.tRPARENTHESIS => {
            builder.advanceLexer //Ate )
          }
          case _ => {
            builder error ScalaBundle.message("rparenthesis.expected")
          }
        }
        builder.restoreNewlinesState
        argMarker.done(ScalaElementTypes.ARG_EXPRS)
        return true
      }
      case ScalaTokenTypes.tLBRACE => {
        if (builder.countNewlineBeforeCurrentToken > 1) {
          argMarker.rollbackTo
          return false
        }
        BlockExpr parse builder
        argMarker.done(ScalaElementTypes.ARG_EXPRS)
        return true
      }
      case _ => {
        argMarker.drop
        return false
      }
    }
  }
}