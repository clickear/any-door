package io.github.lgp547.anydoorplugin.request;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/** Small stateful lexer: request syntax on top, JSON-quality tokens inside bodies. */
final class AnyDoorLexer extends LexerBase {
    private CharSequence text=""; private int end,start,tokenEnd,runPart,directivePart,stringEnd=-1; private IElementType type;
    @Override public void start(CharSequence b,int s,int e,int initial){text=b;start=s;end=e;runPart=0;directivePart=0;stringEnd=-1;locate();}
    @Override public int getState(){return runPart;} @Override public @Nullable IElementType getTokenType(){return type;}
    @Override public int getTokenStart(){return start;} @Override public int getTokenEnd(){return tokenEnd;}
    @Override public void advance(){start=tokenEnd;locate();} @Override public CharSequence getBufferSequence(){return text;} @Override public int getBufferEnd(){return end;}
    private void locate(){
        if(start>=end){type=null;tokenEnd=end;return;} char c=text.charAt(start);
        if(Character.isWhitespace(c)){tokenEnd=start+1;while(tokenEnd<end&&Character.isWhitespace(text.charAt(tokenEnd)))tokenEnd++;if(text.subSequence(start,tokenEnd).toString().indexOf('\n')>=0){runPart=0;directivePart=0;}type=AnyDoorTokenTypes.WHITESPACE;return;}
        if(stringEnd>=start){
            if(start==stringEnd){type=AnyDoorTokenTypes.JSON_STRING;tokenEnd=start+1;stringEnd=-1;return;}
            int variable=indexOf("{{",start);
            if(variable==start){tokenEnd=indexOf("}}",start+2);tokenEnd=tokenEnd<0?stringEnd:Math.min(tokenEnd+2,stringEnd);type=AnyDoorTokenTypes.VARIABLE;return;}
            tokenEnd=variable>=start&&variable<stringEnd?variable:stringEnd;type=AnyDoorTokenTypes.JSON_STRING;return;
        }
        if(isLineStart(start)) { int lineEnd=lineEnd(start); String line=text.subSequence(start,lineEnd).toString();
            if(line.startsWith("###")){type=AnyDoorTokenTypes.TITLE;tokenEnd=lineEnd;return;}
            if(line.startsWith("//")||line.startsWith("#")){type=AnyDoorTokenTypes.COMMENT;tokenEnd=lineEnd;return;}
            if(line.startsWith("@")){type=AnyDoorTokenTypes.DIRECTIVE;tokenEnd=start+1;directivePart=1;return;}
            if(line.startsWith("RUN")&&(line.length()==3||Character.isWhitespace(line.charAt(3)))){type=AnyDoorTokenTypes.RUN;tokenEnd=start+3;runPart=1;return;}
        }
        if(runPart==1){type=AnyDoorTokenTypes.CLASS_REFERENCE;tokenEnd=until(start,"# \t\r\n");runPart=2;return;}
        if(runPart==2&&c=='#'){type=AnyDoorTokenTypes.HASH;tokenEnd=start+1;runPart=3;return;}
        if(runPart==3){type=AnyDoorTokenTypes.METHOD_REFERENCE;tokenEnd=until(start,"( \t\r\n");runPart=4;return;}
        if(runPart==4&&c=='('){type=AnyDoorTokenTypes.TYPE_PARENTHESIS;tokenEnd=start+1;runPart=5;return;}
        if(runPart==5&&c==')'){type=AnyDoorTokenTypes.TYPE_PARENTHESIS;tokenEnd=start+1;runPart=0;return;}
        if(runPart==5&&c==','){type=AnyDoorTokenTypes.TYPE_SEPARATOR;tokenEnd=start+1;return;}
        if(runPart==5){type=AnyDoorTokenTypes.TYPE_REFERENCE;tokenEnd=until(start,",) \t\r\n");return;}
        if(directivePart==1){type=AnyDoorTokenTypes.DIRECTIVE_NAME;tokenEnd=until(start,"= \t\r\n");directivePart=2;return;}
        if(directivePart==2&&c=='='){type=AnyDoorTokenTypes.ASSIGNMENT;tokenEnd=start+1;directivePart=0;return;}
        if(c=='/'&&start+1<end&&text.charAt(start+1)=='/'){type=AnyDoorTokenTypes.COMMENT;tokenEnd=lineEnd(start);return;}
        if(c=='{'&&start+1<end&&text.charAt(start+1)=='{'){tokenEnd=indexOf("}}",start+2);tokenEnd=tokenEnd<0?lineEnd(start):tokenEnd+2;type=AnyDoorTokenTypes.VARIABLE;return;}
        if(c=='"'){tokenEnd=quotedEnd(start);String value=text.subSequence(start,tokenEnd).toString();if(value.contains("{{")){stringEnd=tokenEnd-1;int variable=indexOf("{{",start+1);type=AnyDoorTokenTypes.JSON_STRING;tokenEnd=variable<0?tokenEnd:variable;return;} int p=skipWs(tokenEnd);type=(p<end&&text.charAt(p)==':')?AnyDoorTokenTypes.JSON_KEY:AnyDoorTokenTypes.JSON_STRING;return;}
        if("{}[],:".indexOf(c)>=0){type=AnyDoorTokenTypes.JSON_PUNCTUATION;tokenEnd=start+1;return;}
        if(Character.isDigit(c)||c=='-'){tokenEnd=start+1;while(tokenEnd<end&&"0123456789.eE+-".indexOf(text.charAt(tokenEnd))>=0)tokenEnd++;type=AnyDoorTokenTypes.JSON_NUMBER;return;}
        if(starts("true")||starts("false")){tokenEnd=start+(starts("true")?4:5);type=AnyDoorTokenTypes.JSON_BOOLEAN;return;}
        if(starts("null")){tokenEnd=start+4;type=AnyDoorTokenTypes.JSON_NULL;return;}
        type=AnyDoorTokenTypes.JSON;tokenEnd=start+1;
    }
    private boolean isLineStart(int p){return p==0||text.charAt(p-1)=='\n';} private int lineEnd(int p){while(p<end&&text.charAt(p)!='\n')p++;return p;}
    private int until(int p,String stops){while(p<end&&stops.indexOf(text.charAt(p))<0)p++;return p;} private int skipWs(int p){while(p<end&&Character.isWhitespace(text.charAt(p)))p++;return p;}
    private int quotedEnd(int p){p++;boolean esc=false;while(p<end){char c=text.charAt(p++);if(c=='"'&&!esc)break;esc=c=='\\'&&!esc;if(c!='\\')esc=false;}return p;}
    private int indexOf(String target,int from){for(int i=from;i+target.length()<=end;i++)if(text.subSequence(i,i+target.length()).toString().equals(target))return i;return -1;} private boolean starts(String value){return start+value.length()<=end&&text.subSequence(start,start+value.length()).toString().equals(value);}
}
