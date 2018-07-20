package com.atlassian.uwc.converters.moinmoin;

import java.util.Enumeration;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.atlassian.uwc.converters.BaseConverter;
import com.atlassian.uwc.ui.Page;

public class MoinListConverter extends BaseConverter {

	private Logger log = Logger.getLogger(this.getClass());
	
	protected static final String LSEP = System.getProperty("line.separator", "\n");
	

	
	public void convert(Page page) {
		log.debug("Converting list in Page: " + page.getName());
		
		String cont = page.getOriginalText();
		
		page.setConvertedText(this.convertList(cont));

	}
	

	
	private class Indent {
		
		int their;
		int mine;
		String symbol;
		
//		Indent(int their, int mine){
//			this.their = their;
//			this.mine = mine;
//		}
		
		Indent(int their, int mine, String symbol){
			this.their = their;
			this.mine = mine;
			this.symbol = symbol;
		}
		
		Indent(){
		}
		
		Indent getNew(int theirnew, String symbol){
			return new Indent(theirnew, mine + 1, symbol);
		}
		
		boolean hasNew(int theirnew){
			return theirnew > this.their;
		}
		
		boolean isSame(int theirnew){
			return theirnew == their;
		}
	}
	
	private final Indent first = new Indent(-1,0," ");
	
//	void appendX(StringBuilder sb, indent){
//		for (int i = 0; i < count; i++){
//			sb.append(s);
//		}
//	}
//	
	
	void appendX(StringBuilder sb, Stack<Indent> s){
		for (Enumeration<Indent> e = s.elements(); e.hasMoreElements(); ){
			sb.append( e.nextElement().symbol );
		}
	}
	
	static Pattern startp = Pattern.compile("(\\s*)(([\\*\\.])|([AaIi1]\\.)) +.*", Pattern.MULTILINE);
	static Pattern indented = Pattern.compile("(\\s+).*");
	static Pattern startBlock = Pattern.compile("\\{{3}");
	static Pattern endBlock = Pattern.compile("\\}{3}");

	String convertList(String input){
		StringBuilder output = new StringBuilder();

		final Stack<Indent> intend = new Stack<Indent>();
		intend.push(first);

		// Flag to keep track of an end-of-line that still needs to be added. We don't immediately add eol's after
		// each line. We postpone them to make it possible to join lines that are really a continuation.
		// See below, at the comment "this line is a continuation of the previous indented line"
		boolean doEol = false;
		boolean inBlock = false;
				
		for( String line : input.split("\\r?\\n") ){

			// Track whether or not we're inside a {{{ }}} block (i.e. noformat -- in that case, we don't change anything)
			if (startBlock.matcher(line).find()) {
				if (doEol) {
					output.append(LSEP);
					doEol = false;
				}
				output.append(line);
				output.append(LSEP);
				inBlock = true;
				continue;
			}
			if (endBlock.matcher(line).find()) {
				if (doEol) {
					output.append(LSEP);
					doEol = false;
				}
				output.append(line);
				output.append(LSEP);
				inBlock = false;
				continue;
			}

			if (inBlock) {
				// Inside a {{{ }}} block (= noformat), so simply output the line without any processing
				output.append(line);
				output.append(LSEP);
			} else {
				Matcher mat = startp.matcher(line);
				Matcher indentMat = indented.matcher(line);

				if( mat.matches() ) {

					//log.debug("LINE 1 " + line);

					int intendation = mat.group(1) != null ? mat.group(1).length() : 0;


					// find the different type
					final int symbolLength;
					final String symbol;

					if (mat.group(4) != null) {
						symbol = "#";
						symbolLength = 2;
					} else if (mat.group(3) != null) {
						symbol = "*";
						symbolLength = 1;
					} else { // somehow the line matches but the enum items are not there
						if (doEol) {
							output.append(LSEP);
							doEol = false;
						}
						output.append(line);
						doEol = true;

						// reset the intendation
						intend.clear();
						intend.push(first);
						continue;
					}

					Indent i = intend.peek();


					if (i.hasNew(intendation)) {
						i = i.getNew(intendation, symbol);
						intend.push(i);

					} else if (i.isSame(intendation)) {

					} else {
						// we have lower indentation -> pop until we're at the same level (or lower)
						intend.pop();
						i = intend.peek();
						while (!(i.isSame(intendation) || i.hasNew(intendation)) && i != first) {
							intend.pop();
							i = intend.peek();
						}
						if (i.equals(first)) {
							// Moin Moin has a silly behavior of something like this
							//    * first (level one)
							//  * second level one (this has to be level one but only if they dont have a previous thing
							output.append(LSEP);
							i = i.getNew(1, symbol);
							intend.push(i);
						}
					}

					i.symbol = symbol; //actual current symbol

					// output.append(" ");
					if (doEol) {
						output.append(LSEP);
						doEol = false;
					}
					appendX(output, intend);
					output.append(line.substring(intendation + symbolLength));
					//output.append(LSEP);
					doEol = true;

				} else if ("".equals(line) && !first.equals(intend.peek())) {
					// empty line, but we're still inside indentation -> \\ (otherwise the list gets broken in Confluence)
					if (doEol) {
						output.append(LSEP);
						doEol = false;
					}
					output.append("\\\\");
					output.append(LSEP);
				} else if (indentMat.matches()) {
					int indentation = indentMat.group(1).length();
					if (intend.peek().isSame(indentation)) {
						// this line is a continuation of the previous indented line, so append with space separator.
						output.append(" " + line.substring(indentation));
						doEol = true;
					} else {
						if (doEol) {
							output.append(LSEP);
							doEol = false;
						}
						output.append(line);
						doEol = true;
					}
				} else {

					if (doEol) {
						output.append(LSEP);
						doEol = false;
					}
					//log.debug("LINE 0 " + line);
					// If we're exiting indentation, insert linebreak, otherwise content immediately following the list
					// in MoinMoin might be indented and appear part of the list in Confluence
					if (!first.equals(intend.peek())) {
						output.append(LSEP);
					}

					output.append(line);
					output.append(LSEP);

					// reset the intendation
					intend.clear();
					intend.push(first);
				}
			}
		}
		
		return output.toString();
	}

}
