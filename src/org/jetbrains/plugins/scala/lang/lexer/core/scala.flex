package org.jetbrains.plugins.scala.lang.lexer.core;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypesEx;
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;

%%

%class _ScalaCoreLexer
%implements FlexLexer, ScalaTokenTypesEx
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{
    //do we need to close interpolated String ${}
    private boolean insideInterpolatedStringBracers = false;
    private boolean insideInterpolatedMultilineStringBracers = false;
    //to get id after $ in interpolated String
    private boolean haveIdInString = false;
    private boolean haveIdInMultilineString = false;
    //bracers count inside injection
    private int structuralBracers = 0;

    public boolean isInsideInterpolatedStringInjection() {
      return structuralBracers > 0;
    }

    private IElementType process(IElementType type){
      if (type == tIDENTIFIER && (haveIdInString || haveIdInMultilineString)) {

        if (haveIdInString) {
          haveIdInString = false;
          yybegin(INSIDE_INTERPOLATED_STRING);
        } else {
          haveIdInMultilineString = false;
          yybegin(INSIDE_MULTI_LINE_INTERPOLATED_STRING);
        }
      }

      return type;
    }
%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

integerLiteral = ({decimalNumeral} | {hexNumeral} | {octalNumeral}) (L | l)?
decimalNumeral = 0 | {nonZeroDigit} {digit}*
hexNumeral = 0 x {hexDigit}+
octalNumeral = 0{octalDigit}+
digit = [0-9]
nonZeroDigit = [1-9]
octalDigit = [0-7]
hexDigit = [0-9A-Fa-f]

floatingPointLiteral =
      {digit}+ "." {digit}* {exponentPart}? {floatType}?
    | "." {digit}+ {exponentPart}? {floatType}?
    | {digit}+ {exponentPart} {floatType}?
    | {digit}+ {exponentPart}? {floatType}

exponentPart = (E | e) ("+" | "-")? {digit}+
floatType = F | f | D | d

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

identifier = {plainid} | "`" {stringLiteralExtra} "`"

digit = [0-9]
special = \u0021 | \u0023
          | [\u0025-\u0026]
          | [\u002A-\u002B]
          | \u002D | \u005E
          | \u003A
          | [\u003C-\u0040]
          | \u007E
          | \u005C | \u002F | [:unicode_math_symbol:] | [:unicode_other_symbol:]


// Vertical line
op = \u007C ({special} | \u007C)+
     | {special} ({special} | \u007C)*
octalDigit = [0-7]

idrest1 = [:jletter:]? [:jletterdigit:]* ("_" {op})?
idrest = [:jletter:]? [:jletterdigit:]* ("_" {op} | "_" {idrest1} )?
varid = [:jletter:] {idrest}

plainid = {varid}
          | {op}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

END_OF_LINE_COMMENT="/""/"[^\r\n]*
SH_COMMENT="#!" [^]* "!#" | "::#!" [^]* "::!#"

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & chars //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


ESCAPE_SEQUENCE=\\[^\r\n]
UNICODE_ESCAPE=!(!(\\u{hexDigit}{hexDigit}{hexDigit}{hexDigit}) | \\u000A)
SOME_ESCAPE=\\{octalDigit} {octalDigit}? {octalDigit}?
CHARACTER_LITERAL="'"([^\\\'\r\n]|{ESCAPE_SEQUENCE}|{UNICODE_ESCAPE}|{SOME_ESCAPE})("'"|\\) | \'\\u000A\'

STRING_BEGIN = \"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*
STRING_LITERAL={STRING_BEGIN} \"
MULTI_LINE_STRING = \"\"\" ( (\"(\")?)? [^\"] )* \"\"\" (\")* // Multi-line string

////////String Interpolation////////
INTERPOLATED_STRING_ID = ("s"|"f"|"id")

INTERPOLATED_STRING_BEGIN = \"([^\\\"\r\n\$]|{ESCAPE_SEQUENCE})*
INTERPOLATED_STRING_PART = ([^\\\"\r\n\$]|{ESCAPE_SEQUENCE})*

INTERPOLATED_MULTI_LINE_STRING_BEGIN = \"\"\" ( (\"(\")?)? [^\"\$] )*
INTERPOLATED_MULTI_LINE_STRING_PART = ( (\"(\")?)? [^\"\$] )*

INTERPOLATED_STRING_ESCAPE = "$$"
INTERPOLATED_STRING_VARIABLE = "$"({identifier})
//INTERPOLATED_STRING_EXPRESSION_START = "${"
////////////////////////////////////


WRONG_STRING = {STRING_BEGIN}

charEscapeSeq = \\[^\r\n]
charNoDoubleQuote = !( ![^"\""] | {LineTerminator})
stringElement = {charNoDoubleQuote} | {charEscapeSeq}  
stringLiteral = {stringElement}*
charExtra = !( ![^"\""`] | {LineTerminator})             //This is for `type` identifiers
stringElementExtra = {charExtra} | {charEscapeSeq}
stringLiteralExtra = {stringElementExtra}*
symbolLiteral = "\'" {plainid}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Common symbols //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

LineTerminator = \r | \n | \r\n | \u0085 |  \u2028 | \u2029 | \u000A | \u000a
WhiteSpace = " " | "\t" | "\f"
mNLS = {LineTerminator} ({LineTerminator} | {WhiteSpace})*

XML_BEGIN = "<" ("_" | [:jletter:]) | "<!--" | "<?" ("_" | [:jletter:]) | "<![CDATA["


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%state COMMON_STATE
%xstate WAIT_FOR_INTERPOLATED_STRING
%xstate INSIDE_INTERPOLATED_STRING
%xstate INSIDE_MULTI_LINE_INTERPOLATED_STRING

%%

//YYINITIAL is alias for WAIT_FOR_XML, so state will be initial after every space or new line token
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////  XML processing ///////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<YYINITIAL>{

{XML_BEGIN}                             {   yybegin(COMMON_STATE);
                                            yypushback(yytext().length());
                                            return SCALA_XML_CONTENT_START;
                                        }
}

{END_OF_LINE_COMMENT}                   { return process(tLINE_COMMENT); }

{SH_COMMENT}                            { return process(tSH_COMMENT); }


{INTERPOLATED_STRING_ID} / ({INTERPOLATED_STRING_BEGIN} | {INTERPOLATED_MULTI_LINE_STRING_BEGIN}) {
  yybegin(WAIT_FOR_INTERPOLATED_STRING);
  return process(tINTERPOLATED_STRING_ID);
}

<WAIT_FOR_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_BEGIN} {
    yybegin(INSIDE_INTERPOLATED_STRING);
    return process(tINTERPOLATED_STRING);
  }

  {INTERPOLATED_MULTI_LINE_STRING_BEGIN} {
    yybegin(INSIDE_MULTI_LINE_INTERPOLATED_STRING);
    return process(tINTERPOLATED_MULTILINE_STRING);
  }
}

<INSIDE_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_ESCAPE} {
    return process(tINTERPOLATED_STRING_ESCAPE);
  }

  {INTERPOLATED_STRING_PART} {
    return process(tINTERPOLATED_STRING);
  }

  "$"{identifier} {
    if (yycharat(1) != '$') {
      haveIdInString = true;
      yybegin(COMMON_STATE);
      yypushback(yytext().length() - 1);
      return process(tINTERPOLATED_STRING_INJECTION);
    } else {
      yypushback(yytext().length() - 2);
      return process(tINTERPOLATED_STRING_ESCAPE);
    }
  }

  \" {
    yybegin(COMMON_STATE);
    return process(tINTERPOLATED_STRING_END);
  }

  "$" / "{" {
    yybegin(COMMON_STATE);
    insideInterpolatedStringBracers = true;
    return process(tINTERPOLATED_STRING_INJECTION);
  }

  [^] {
    yybegin(COMMON_STATE);
    return process(tWRONG_STRING);
  }
}

<INSIDE_MULTI_LINE_INTERPOLATED_STRING> {
  {INTERPOLATED_STRING_ESCAPE} {
    return process(tINTERPOLATED_STRING_ESCAPE);
  }

  {INTERPOLATED_MULTI_LINE_STRING_PART} {
    return process(tINTERPOLATED_MULTILINE_STRING);
  }

  "$"{identifier} {
    if (yycharat(1) != '$') {
      haveIdInMultilineString = true;
      yybegin(COMMON_STATE);
      yypushback(yytext().length() - 1);
      return process(tINTERPOLATED_STRING_INJECTION);
    } else {
      yypushback(yytext().length() - 2);
      return process(tINTERPOLATED_STRING_ESCAPE);
    }
  }

  \"\"\" {
      yybegin(COMMON_STATE);
      return process(tINTERPOLATED_STRING_END);
  }

  "$" / "{" {
      yybegin(COMMON_STATE);
      insideInterpolatedMultilineStringBracers = true;
      return process(tINTERPOLATED_STRING_INJECTION);
  }

  [^] {   yybegin(COMMON_STATE);
    yypushback(yytext().length());
  }
}


{STRING_LITERAL}                        {   return process(tSTRING);  }

{MULTI_LINE_STRING}                     {   return process(tMULTILINE_STRING);  }

{WRONG_STRING}                          {   return process(tWRONG_STRING);  }


{symbolLiteral}                          {  return process(tSYMBOL);  }

{CHARACTER_LITERAL}                      {  return process(tCHAR);  }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// braces ///////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
"["                                     {   return process(tLSQBRACKET); }
"]"                                     {   return process(tRSQBRACKET); }

"{"                                     {   if (insideInterpolatedStringBracers || insideInterpolatedMultilineStringBracers) {
                                              ++structuralBracers;
                                            }

                                            return process(tLBRACE); }
"{"{XML_BEGIN}                          {   if (insideInterpolatedStringBracers || insideInterpolatedMultilineStringBracers) {
                                              ++structuralBracers;
                                            }
                                            yypushback(yytext().length() - 1);
                                            yybegin(YYINITIAL);
                                            return process(tLBRACE); }

"}"                                     {   if (insideInterpolatedStringBracers || insideInterpolatedMultilineStringBracers) {
                                              --structuralBracers;
                                              if (structuralBracers == 0) {
                                                 if (insideInterpolatedMultilineStringBracers) {
                                                    yybegin(INSIDE_MULTI_LINE_INTERPOLATED_STRING);
                                                    insideInterpolatedMultilineStringBracers = false;
                                                 } else {
                                                    yybegin(INSIDE_INTERPOLATED_STRING);
                                                    insideInterpolatedStringBracers = false;
                                                 }
                                              }

                                            }
                                            return process(tRBRACE); }

"("                                     {   return process(tLPARENTHESIS); }

"("{XML_BEGIN}                          {   yypushback(yytext().length() - 1);
                                            yybegin(YYINITIAL);
                                            return process(tLPARENTHESIS); }
")"                                     {   return process(tRPARENTHESIS); }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"abstract"                              {   return process(kABSTRACT); }

"case" / ({LineTerminator}|{WhiteSpace})+("class" | "object")
                                        {   return process(kCASE); }

"case"                                  {   return process(kCASE); }
                                            
"catch"                                 {   return process(kCATCH); }
"class"                                 {   return process(kCLASS); }
"def"                                   {   return process(kDEF); }
"do"                                    {   return process(kDO); }
"else"                                  {   return process(kELSE); }
"extends"                               {   return process(kEXTENDS); }
"false"                                 {   return process(kFALSE); }
"final"                                 {   return process(kFINAL); }
"finally"                               {   return process(kFINALLY); }
"for"                                   {   return process(kFOR); }
"forSome"                               {   return process(kFOR_SOME); }
"if"                                    {   return process(kIF); }
"implicit"                              {   return process(kIMPLICIT); }
"import"                                {   return process(kIMPORT); }
"lazy"                                  {   return process(kLAZY); }
"match"                                 {   return process(kMATCH); }
"new"                                   {   return process(kNEW); }
"null"                                  {   return process(kNULL); }
"object"                                {   return process(kOBJECT); }
"override"                              {   return process(kOVERRIDE); }
"package"                               {   return process(kPACKAGE); }
"private"                               {   return process(kPRIVATE); }
"protected"                             {   return process(kPROTECTED); }
"requires"                              {   return process(kREQUIRES); }
"return"                                {   return process(kRETURN); }
"sealed"                                {   return process(kSEALED); }
"super"                                 {   return process(kSUPER); }
"this"                                  {   return process(kTHIS); }
"throw"                                 {   return process(kTHROW); }
"trait"                                 {   return process(kTRAIT); }
"try"                                   {   return process(kTRY); }
"true"                                  {   return process(kTRUE); }
"type"                                  {   return process(kTYPE); }
"val"                                   {   return process(kVAL); }
"var"                                   {   return process(kVAR); }
"while"                                 {   return process(kWHILE); }
"with"                                  {   return process(kWITH); }
"yield"                                 {   return process(kYIELD); }
"macro"                                 {   return process(kMACRO); }

///////////////////// Reserved shorthands //////////////////////////////////////////

"*"                                     {   return process(tIDENTIFIER);  }
"?"                                     {   return process(tIDENTIFIER);  }

"_"                                     {   return process(tUNDER);  }
":"                                     {   return process(tCOLON);  }
"="                                     {   return process(tASSIGN);  }

"=>"                                    {   return process(tFUNTYPE); }
"\\u21D2"                               {   return process(tFUNTYPE); }
"\u21D2"                                {   return process(tFUNTYPE); }

"<-"                                    {   return process(tCHOOSE); }
"\\u2190"                               {   return process(tCHOOSE); }
"\u2190"                                {   return process(tCHOOSE); }

"<:"                                    {   return process(tUPPER_BOUND); }
">:"                                    {   return process(tLOWER_BOUND); }
"<%"                                    {   return process(tVIEW); }
"#"                                     {   return process(tINNER_CLASS); }
"@"                                     {   return process(tAT);}

"&"                                     {   return process(tIDENTIFIER);}
"|"                                     {   return process(tIDENTIFIER);}
"+"                                     {   return process(tIDENTIFIER); }
"-"                                     {   return process(tIDENTIFIER);}
"~"                                     {   return process(tIDENTIFIER);}
"!"                                     {   return process(tIDENTIFIER);}

"."                                     {   return process(tDOT);}
";"                                     {   return process(tSEMICOLON);}
","                                     {   return process(tCOMMA);}


{identifier}                            {   return process(tIDENTIFIER); }
{integerLiteral} / "." {identifier}
                                        {   return process(tINTEGER);  }
{floatingPointLiteral}                  {   return process(tFLOAT);      }
{integerLiteral}                        {   return process(tINTEGER);  }
{WhiteSpace}                            {   yybegin(YYINITIAL);
                                            return process(tWHITE_SPACE_IN_LINE);  }
{mNLS}                                  {   yybegin(YYINITIAL);
                                            return process(tWHITE_SPACE_IN_LINE); }

////////////////////// STUB ///////////////////////////////////////////////
.                                       {   return process(tSTUB); }

